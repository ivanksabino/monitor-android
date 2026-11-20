package dev.monitorcidadao.core.network

import dev.monitorcidadao.core.network.auth.InMemoryAccessTokenStore
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.milliseconds

/** Correções da revisão da Fundação: imagens, redirects, dedup, headers e tetos de tempo. */
class NetworkHardeningTest {
    private lateinit var server: MockWebServer

    @BeforeEach
    fun setUp() {
        server = MockWebServer().apply { start() }
    }

    @AfterEach
    fun tearDown() = server.close()

    /** Faz o papel do FixtureInterceptor: responde sem ir à rede. */
    private val fakeServer = Interceptor { chain ->
        Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(404).message("Fixture")
            .body("{}".toResponseBody(null)).build()
    }

    @Nested
    inner class Imagens {
        private val client by lazy {
            HTTPClient.create(
                server.config {
                    copy(tokenStore = InMemoryAccessTokenStore("segredo"), extraInterceptors = listOf(fakeServer))
                },
            )
        }

        @Test
        fun `imagem nao leva token, nao passa pela fixture e nao forca Accept json`() {
            server.enqueue(MockResponse.Builder().code(200).body("png").build())
            val image = client.imageCallFactory()
                .newCall(Request.Builder().url(server.url("/foto.png")).header("Accept", "image/*").build())
                .execute()
            image.use { assertEquals(200, it.code) }

            val recorded = server.takeRequest(2, TimeUnit.SECONDS)!!
            assertNull(recorded.headers["Authorization"])
            assertEquals("image/*", recorded.headers["Accept"])
            assertEquals(1, server.requestCount, "chegou ao servidor (não foi respondida pela fixture)")
        }

        @Test
        fun `imagem tem Dispatcher proprio e compartilha pool e seguranca`() {
            val images = client.imageCallFactory() as OkHttpClient
            assertNotSame(client.okHttpClient.dispatcher, images.dispatcher)
            assertEquals(client.okHttpClient.connectionPool, images.connectionPool)
            assertEquals(client.okHttpClient.certificatePinner, images.certificatePinner)
            assertTrue(images.interceptors.isEmpty())
        }
    }

    @Nested
    inner class Redirect {
        @Test
        fun `redirect para outro host nao leva Authorization`() = runBlocking {
            MockWebServer().apply { start() }.use { destino ->
                destino.enqueue(ok("destino"))
                server.enqueue(
                    MockResponse.Builder().code(302).addHeader("Location", destino.url("/v1/eco").toString()).build(),
                )
                val client = HTTPClient.create(server.config { copy(tokenStore = InMemoryAccessTokenStore("segredo")) })

                assertEquals(Eco("destino"), client.send(ecoEndpoint()))
                assertEquals("Bearer segredo", server.takeRequest(2, TimeUnit.SECONDS)!!.headers["Authorization"])
                assertNull(destino.takeRequest(2, TimeUnit.SECONDS)!!.headers["Authorization"])
            }
        }

        @Test
        fun `nao segue redirect de https para http`() {
            assertEquals(false, HTTPClient.create(server.config()).okHttpClient.followSslRedirects)
        }
    }

    @Nested
    inner class DedupComPolitica {
        @Test
        fun `NoCache nunca pega carona numa GET Default em voo`() = runBlocking(Dispatchers.IO) {
            val gate = GateDispatcher { ok(it.headers["Cache-Control"] ?: "default") }
            server.dispatcher = gate
            val client = HTTPClient.create(server.config())

            val default = async(start = CoroutineStart.UNDISPATCHED) { client.send(ecoEndpoint(cursor = "p1")) }
            gate.firstArrived.await(5, TimeUnit.SECONDS)
            val refresh = async(start = CoroutineStart.UNDISPATCHED) {
                client.send(ecoEndpoint(cursor = "p1").copy(cachePolicy = CachePolicy.NoCache))
            }
            waitUntil { gate.arrived.size == 2 }
            gate.release()

            assertEquals(Eco("default"), default.await())
            assertEquals(Eco("no-cache"), refresh.await())
            assertEquals(2, server.requestCount)
        }

        @Test
        fun `headers diferentes nao se misturam`() = runBlocking(Dispatchers.IO) {
            val gate = GateDispatcher { ok(it.headers["X-Variante"] ?: "-") }
            server.dispatcher = gate
            val client = HTTPClient.create(server.config())

            val a = async(start = CoroutineStart.UNDISPATCHED) {
                client.send(ecoEndpoint().copy(headers = mapOf("X-Variante" to "a")))
            }
            val b = async(start = CoroutineStart.UNDISPATCHED) {
                client.send(ecoEndpoint().copy(headers = mapOf("X-Variante" to "b")))
            }
            waitUntil { gate.arrived.size == 2 }
            gate.release()
            assertEquals(listOf(Eco("a"), Eco("b")), listOf(a.await(), b.await()))
        }
    }

    @Nested
    inner class Cabecalhos {
        @Test
        fun `Accept e Accept-Language do endpoint prevalecem`() = runBlocking {
            server.enqueue(ok())
            val client = HTTPClient.create(server.config())
            client.send(ecoEndpoint().copy(headers = mapOf("Accept" to "application/geo+json", "Accept-Language" to "en")))
            val recorded = server.takeRequest(2, TimeUnit.SECONDS)!!
            assertEquals("application/geo+json", recorded.headers["Accept"])
            assertEquals("en", recorded.headers["Accept-Language"])
        }

        @Test
        fun `sem pedido do endpoint vale o padrao`() = runBlocking {
            server.enqueue(ok())
            HTTPClient.create(server.config()).send(ecoEndpoint())
            val recorded = server.takeRequest(2, TimeUnit.SECONDS)!!
            assertEquals("application/json", recorded.headers["Accept"])
            assertEquals("pt-BR", recorded.headers["Accept-Language"])
        }
    }

    @Nested
    inner class TetosDeTempo {
        @Test
        fun `callTimeout limita a chamada inteira e vira Timeout`() = runBlocking {
            server.enqueue(ok().newBuilder().headersDelay(2, TimeUnit.SECONDS).build())
            val client = HTTPClient.create(server.config { copy(callTimeout = 200.milliseconds, retryPolicy = null) })
            assertThrows<NetworkError.Timeout> { client.send(ecoEndpoint()) }
            Unit
        }

        @Test
        fun `GET de Fundo nao segura vaga esperando Retry-After`() = runBlocking {
            val sleeper = RecordingSleeper()
            server.enqueue(MockResponse.Builder().code(429).addHeader("Retry-After", "3").build())
            val client = HTTPClient.create(server.config(sleeper))
            val error = assertThrows<NetworkError.Http> { client.send(ecoEndpoint(), Priority.Fundo) }
            assertEquals(429, error.status)
            assertEquals(1, server.requestCount)
            assertTrue(sleeper.sleeps.isEmpty())
        }

        @Test
        fun `Retry-After acima do teto de tela volta para quem chamou`() = runBlocking {
            val sleeper = RecordingSleeper()
            server.enqueue(MockResponse.Builder().code(503).addHeader("Retry-After", "20").build())
            val client = HTTPClient.create(server.config(sleeper))
            val error = assertThrows<NetworkError.Http> { client.send(ecoEndpoint(), Priority.Alta) }
            assertEquals(503, error.status)
            assertTrue(sleeper.sleeps.isEmpty())
        }
    }
}

package dev.monitorcidadao.core.network

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class HTTPClientTest {
    private lateinit var server: MockWebServer

    @BeforeEach
    fun setUp() {
        server = MockWebServer().apply { start() }
    }

    @AfterEach
    fun tearDown() {
        server.close()
    }

    @Nested
    inner class Requisicao {
        @Test
        fun `monta url com query, cabecalhos e decodifica`() = runBlocking {
            server.enqueue(ok("cam-1001"))
            val client = HTTPClient.create(server.config())

            val result = client.send(ecoEndpoint("/v1/pessoas/cam-1001/posts", cursor = "p2"))

            assertEquals(Eco("cam-1001"), result)
            val recorded = server.takeRequest()
            assertEquals("/v1/pessoas/cam-1001/posts?cursor=p2", recorded.target)
            assertEquals("application/json", recorded.headers["Accept"])
            assertEquals("pt-BR", recorded.headers["Accept-Language"])
            assertEquals("MonitorCidadao-Teste/1.0", recorded.headers["User-Agent"])
            assertNull(recorded.headers["Authorization"], "MVP sem login não manda token")
        }

        @Test
        fun `query nula e omitida e 204 vira Unit`() = runBlocking {
            server.enqueue(MockResponse.Builder().code(204).build())
            val client = HTTPClient.create(server.config())
            assertEquals(Unit, client.send(semCorpo("/v1/seguindo/cam-1001", HttpMethod.POST)))
            val recorded = server.takeRequest()
            assertEquals("POST", recorded.method)
            assertEquals("/v1/seguindo/cam-1001", recorded.target)
        }

        @Test
        fun `prioridade vai como tag do request`() {
            val client = HTTPClient.create(server.config())
            val request = client.buildRequest(ecoEndpoint(), Priority.Utilidade)
            assertEquals(Priority.Utilidade, request.tag(Priority::class.java))
        }
    }

    @Nested
    inner class Erros {
        @Test
        fun `corpo de erro do contrato e decodificado`() = runBlocking {
            server.enqueue(
                MockResponse.Builder().code(404)
                    .body("""{"erro":{"codigo":"pessoa_nao_encontrada","mensagem":"Pessoa não encontrada"}}""").build(),
            )
            val client = HTTPClient.create(server.config())
            val error = assertThrows<NetworkError.Http> { client.send(ecoEndpoint()) }
            assertEquals(404, error.status)
            assertEquals(ApiError("pessoa_nao_encontrada", "Pessoa não encontrada"), error.apiError)
        }

        @Test
        fun `corpo de erro fora do contrato nao quebra`() = runBlocking {
            server.enqueue(MockResponse.Builder().code(500).body("<html>oops</html>").build())
            val client = HTTPClient.create(server.config { copy(retryPolicy = null) })
            val error = assertThrows<NetworkError.Http> { client.send(ecoEndpoint()) }
            assertEquals(500, error.status)
            assertNull(error.apiError)
        }

        @Test
        fun `json invalido no sucesso vira Decoding`() = runBlocking {
            server.enqueue(MockResponse.Builder().code(200).body("""{"outro":1}""").build())
            val client = HTTPClient.create(server.config())
            assertThrows<NetworkError.Decoding> { client.send(ecoEndpoint()) }
            Unit
        }

        @Test
        fun `campo novo no json e ignorado`() = runBlocking {
            server.enqueue(MockResponse.Builder().code(200).body("""{"valor":"x","novo":true}""").build())
            assertEquals(Eco("x"), HTTPClient.create(server.config()).send(ecoEndpoint()))
        }

        @Test
        fun `429 traz Retry-After no erro`() = runBlocking {
            server.enqueue(MockResponse.Builder().code(429).addHeader("Retry-After", "120").build())
            val client = HTTPClient.create(server.config())
            val error = assertThrows<NetworkError.Http> { client.send(ecoEndpoint()) }
            assertEquals(429, error.status)
            assertEquals(120.seconds, error.retryAfter)
        }

        @Test
        fun `tempo esgotado vira Timeout`() = runBlocking {
            server.enqueue(ok().newBuilder().headersDelay(2, TimeUnit.SECONDS).build())
            val client = HTTPClient.create(server.config { copy(readTimeout = 200.milliseconds, retryPolicy = null) })
            assertThrows<NetworkError.Timeout> { client.send(ecoEndpoint()) }
            Unit
        }

        @Test
        fun `servidor fora do ar vira NoConnection`() = runBlocking {
            val config = server.config { copy(retryPolicy = null) }
            server.close()
            assertThrows<NetworkError.NoConnection> { HTTPClient.create(config).send(ecoEndpoint()) }
            Unit
        }
    }

    @Nested
    inner class Dedup {
        @Test
        fun `GET identico em voo vira uma chamada so`() = runBlocking(Dispatchers.IO) {
            val gate = GateDispatcher { ok("compartilhado") }
            server.dispatcher = gate
            val client = HTTPClient.create(server.config())

            val a = async(start = CoroutineStart.UNDISPATCHED) { client.send(ecoEndpoint(cursor = "p2")) }
            val b = async(start = CoroutineStart.UNDISPATCHED) { client.send(ecoEndpoint(cursor = "p2")) }
            gate.firstArrived.await(5, TimeUnit.SECONDS)
            gate.release()

            assertEquals(Eco("compartilhado"), a.await())
            assertEquals(Eco("compartilhado"), b.await())
            assertEquals(1, server.requestCount)
        }

        @Test
        fun `cursores diferentes nunca se misturam`() = runBlocking(Dispatchers.IO) {
            val gate = GateDispatcher { ok(it.url.queryParameter("cursor") ?: "p1") }
            server.dispatcher = gate
            val client = HTTPClient.create(server.config())

            val p1 = async(start = CoroutineStart.UNDISPATCHED) { client.send(ecoEndpoint()) }
            val p2 = async(start = CoroutineStart.UNDISPATCHED) { client.send(ecoEndpoint(cursor = "p2")) }
            val p3 = async(start = CoroutineStart.UNDISPATCHED) { client.send(ecoEndpoint(cursor = "p3")) }
            waitUntil { gate.arrived.size == 3 }
            gate.release()

            assertEquals(listOf(Eco("p1"), Eco("p2"), Eco("p3")), listOf(p1.await(), p2.await(), p3.await()))
            assertEquals(3, server.requestCount)
        }

        @Test
        fun `POST nunca e deduplicado`() = runBlocking(Dispatchers.IO) {
            server.dispatcher = GateDispatcher { MockResponse.Builder().code(204).build() }.also { it.release() }
            val client = HTTPClient.create(server.config())
            val a = async { client.send(semCorpo("/v1/seguindo/x", HttpMethod.POST)) }
            val b = async { client.send(semCorpo("/v1/seguindo/x", HttpMethod.POST)) }
            a.await()
            b.await()
            assertEquals(2, server.requestCount)
        }
    }

    @Nested
    inner class Cancelamento {
        @Test
        fun `cancelar a coroutine cancela a Call do OkHttp`() = runBlocking(Dispatchers.IO) {
            server.enqueue(ok().newBuilder().headersDelay(10, TimeUnit.SECONDS).build())
            val events = CallEvents()
            val client = HTTPClient.create(server.config { copy(eventListenerFactory = events) })

            val job = launch { client.send(ecoEndpoint()) }
            server.takeRequest(5, TimeUnit.SECONDS)
            withTimeout(2_000) { job.cancelAndJoin() }

            assertTrue(job.isCancelled)
            assertTrue(events.canceled.await(2, TimeUnit.SECONDS), "OkHttp recebeu o cancelamento")
            waitUntil { client.okHttpClient.dispatcher.runningCallsCount() == 0 }
        }

        @Test
        fun `um interessado desiste e o outro ainda recebe`() = runBlocking(Dispatchers.IO) {
            val gate = GateDispatcher { ok("ainda") }
            server.dispatcher = gate
            val events = CallEvents()
            val client = HTTPClient.create(server.config { copy(eventListenerFactory = events) })

            val desiste = async(start = CoroutineStart.UNDISPATCHED) { client.send(ecoEndpoint()) }
            val fica = async(start = CoroutineStart.UNDISPATCHED) { client.send(ecoEndpoint()) }
            gate.firstArrived.await(5, TimeUnit.SECONDS)
            desiste.cancelAndJoin()
            gate.release()

            assertEquals(Eco("ainda"), fica.await())
            assertEquals(0, events.canceledCount.get())
        }

        @Test
        fun `todos desistem e a chamada compartilhada e cancelada`() = runBlocking(Dispatchers.IO) {
            val gate = GateDispatcher()
            server.dispatcher = gate
            val events = CallEvents()
            val client = HTTPClient.create(server.config { copy(eventListenerFactory = events) })

            val a = async(start = CoroutineStart.UNDISPATCHED) { client.send(ecoEndpoint()) }
            val b = async(start = CoroutineStart.UNDISPATCHED) { client.send(ecoEndpoint()) }
            gate.firstArrived.await(5, TimeUnit.SECONDS)
            a.cancelAndJoin()
            b.cancelAndJoin()

            assertTrue(events.canceled.await(2, TimeUnit.SECONDS))
            gate.release()
        }

        @Test
        fun `cancelar durante o backoff do retry interrompe a espera`() = runBlocking(Dispatchers.IO) {
            server.enqueue(MockResponse.Builder().code(503).build())
            server.enqueue(ok())
            val events = CallEvents()
            // sleeper real com backoff longo: só o cancelamento tira a chamada da espera
            val client = HTTPClient.create(
                server.config {
                    copy(
                        retrySleeper = dev.monitorcidadao.core.network.interceptor.Sleeper.Real,
                        retryPolicy = dev.monitorcidadao.core.network.interceptor.RetryPolicy(baseDelay = 20.seconds, maxDelay = 20.seconds),
                        eventListenerFactory = events,
                    )
                },
            )
            val job = launch { client.send(ecoEndpoint()) }
            server.takeRequest(5, TimeUnit.SECONDS)
            withTimeout(2_000) { job.cancelAndJoin() }
            assertTrue(events.canceled.await(2, TimeUnit.SECONDS))
            assertEquals(1, server.requestCount)
        }
    }

    @Nested
    inner class Prioridade {
        @Test
        fun `com o host lotado a proxima vaga vai para a prioridade mais alta`() = runBlocking(Dispatchers.IO) {
            val gate = GateDispatcher()
            server.dispatcher = gate
            val client = HTTPClient.create(server.config { copy(maxConcurrentPerHost = 1) })

            val ocupa = async(start = CoroutineStart.UNDISPATCHED) { client.send(ecoEndpoint("/ocupa")) }
            gate.firstArrived.await(5, TimeUnit.SECONDS)
            val fundo = async(start = CoroutineStart.UNDISPATCHED) { client.send(ecoEndpoint("/fundo"), Priority.Fundo) }
            val utilidade = async(start = CoroutineStart.UNDISPATCHED) { client.send(ecoEndpoint("/utilidade"), Priority.Utilidade) }
            val alta = async(start = CoroutineStart.UNDISPATCHED) { client.send(ecoEndpoint("/alta"), Priority.Alta) }
            gate.release()
            listOf(ocupa, fundo, utilidade, alta).forEach { it.await() }

            assertEquals(listOf("/ocupa", "/alta", "/utilidade", "/fundo"), gate.arrived.toList())
        }
    }

    @Test
    fun `erro de transporte que nao e de rede vira NetworkError tipado`() {
        assertInstanceOf(NetworkError.Security::class.java, javax.net.ssl.SSLHandshakeException("x").toNetworkError())
        assertInstanceOf(NetworkError.Cancelled::class.java, java.io.IOException("Canceled").toNetworkError())
        assertInstanceOf(NetworkError.Cancelled::class.java, java.io.IOException("qualquer").toNetworkError(callCanceled = true))
        assertInstanceOf(NetworkError.NoConnection::class.java, java.net.UnknownHostException("x").toNetworkError())
        assertInstanceOf(NetworkError.Unknown::class.java, IllegalStateException("x").toNetworkError())
    }
}

package dev.monitorcidadao.core.network

import dev.monitorcidadao.core.network.auth.InMemoryAccessTokenStore
import dev.monitorcidadao.core.network.auth.TokenRefresher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.Request
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.time.Duration.Companion.milliseconds
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.concurrent.atomic.AtomicInteger

class AuthTest {
    private lateinit var server: MockWebServer

    @BeforeEach
    fun setUp() {
        server = MockWebServer().apply {
            dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse =
                    if (request.headers["Authorization"] == "Bearer novo") ok("autorizado")
                    else MockResponse.Builder().code(401).build()
            }
            start()
        }
    }

    @AfterEach
    fun tearDown() = server.close()

    @Test
    fun `varios 401 simultaneos geram uma unica renovacao`() = runBlocking(Dispatchers.IO) {
        val refreshes = AtomicInteger()
        val store = InMemoryAccessTokenStore("velho")
        val client = HTTPClient.create(
            server.config {
                copy(
                    tokenStore = store,
                    tokenRefresher = TokenRefresher {
                        refreshes.incrementAndGet()
                        delay(100)
                        "novo"
                    },
                )
            },
        )

        val results = (1..5).map { i -> async { client.send(ecoEndpoint(cursor = "c$i")) } }.awaitAll()

        assertEquals(List(5) { Eco("autorizado") }, results)
        assertEquals(1, refreshes.get())
        assertEquals("novo", store.current())
    }

    @Test
    fun `401 de novo depois de renovar nao entra em loop`() = runBlocking {
        val sempre401 = MockWebServer().apply {
            dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest) = MockResponse.Builder().code(401).build()
            }
            start()
        }
        sempre401.use {
            val refreshes = AtomicInteger()
            val client = HTTPClient.create(
                it.config {
                    copy(
                        tokenStore = InMemoryAccessTokenStore("velho"),
                        tokenRefresher = TokenRefresher { _ -> "novo-${refreshes.incrementAndGet()}" },
                    )
                },
            )
            val error = assertThrows<NetworkError.Http> { client.send(ecoEndpoint()) }
            assertEquals(401, error.status)
            assertEquals(2, it.requestCount)
            assertEquals(1, refreshes.get())
        }
    }

    @Test
    fun `401 em POST renova e repete uma vez`() = runBlocking {
        val client = HTTPClient.create(
            server.config { copy(tokenStore = InMemoryAccessTokenStore("velho"), tokenRefresher = TokenRefresher { "novo" }) },
        )
        assertEquals(Eco("autorizado"), client.send(ecoEndpoint(method = HttpMethod.POST)))
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `renovacao que lanca vira NetworkError tipado e nada escapa para o Dispatcher`() = runBlocking(Dispatchers.IO) {
        val escaped = CopyOnWriteArrayList<Throwable>()
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { _, e -> escaped += e }
        try {
            fun client(refresher: TokenRefresher) = HTTPClient.create(
                server.config { copy(tokenStore = InMemoryAccessTokenStore("velho"), tokenRefresher = refresher) },
            )
            val http = assertThrows<NetworkError.Http> { client { throw NetworkError.Http(500) }.send(ecoEndpoint()) }
            assertEquals(500, http.status)
            assertThrows<NetworkError.Unknown> { client { throw IllegalStateException("bug") }.send(ecoEndpoint()) }
            assertThrows<NetworkError.Unknown> {
                client { throw kotlinx.coroutines.CancellationException("cancelada") }.send(ecoEndpoint())
            }
            Thread.sleep(200) // dá tempo de um eventual relançamento na thread do Dispatcher
            assertEquals(emptyList<Throwable>(), escaped.toList())
        } finally {
            Thread.setDefaultUncaughtExceptionHandler(previous)
        }
    }

    @Test
    fun `renovacao pendurada estoura o tempo e vira Timeout`() = runBlocking(Dispatchers.IO) {
        val client = HTTPClient.create(
            server.config {
                copy(
                    tokenStore = InMemoryAccessTokenStore("velho"),
                    tokenRefresher = TokenRefresher { delay(10_000); "novo" },
                    tokenRefreshTimeout = 100.milliseconds,
                )
            },
        )
        assertThrows<NetworkError.Timeout> { client.send(ecoEndpoint()) }
        Unit
    }

    @Test
    fun `host lotado de 401 nao trava a renovacao feita no mesmo servidor`() = runBlocking(Dispatchers.IO) {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when {
                request.url.encodedPath == "/v1/token" -> MockResponse.Builder().code(200).body("novo").build()
                request.headers["Authorization"] == "Bearer novo" -> ok("autorizado")
                else -> MockResponse.Builder().code(401).build()
            }
        }
        val tokenUrl = server.url("/v1/token")
        val client = HTTPClient.create(
            server.config {
                copy(
                    maxConcurrentPerHost = 4,
                    tokenStore = InMemoryAccessTokenStore("velho"),
                    tokenRefresher = TokenRefresher { http ->
                        delay(100) // deixa as 4 chamadas ocuparem todas as vagas antes
                        http.newCall(Request.Builder().url(tokenUrl).build()).execute().use { it.body.string() }
                    },
                )
            },
        )
        val results = withTimeout(10_000) {
            (1..6).map { i -> async { client.send(ecoEndpoint(cursor = "c$i")) } }.awaitAll()
        }
        assertEquals(List(6) { Eco("autorizado") }, results)
        val tokenRequest = (0 until server.requestCount).map { server.takeRequest() }.first { it.url.encodedPath == "/v1/token" }
        assertNull(tokenRequest.headers["Authorization"], "renovação não passa pelo AuthInterceptor")
    }

    @Test
    fun `sem renovacao possivel o 401 chega a quem chamou`() = runBlocking {
        val client = HTTPClient.create(server.config { copy(tokenStore = InMemoryAccessTokenStore("velho")) })
        val error = assertThrows<NetworkError.Http> { client.send(ecoEndpoint()) }
        assertEquals(401, error.status)
        assertEquals(1, server.requestCount)
    }
}

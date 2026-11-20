package dev.monitorcidadao.core.network

import dev.monitorcidadao.core.network.interceptor.RetryPolicy
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.SocketEffect
import okhttp3.Headers.Companion.headersOf
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import java.time.Instant
import kotlin.random.Random
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class RetryInterceptorTest {
    private lateinit var server: MockWebServer
    private val sleeper = RecordingSleeper()

    @BeforeEach
    fun setUp() {
        server = MockWebServer().apply { start() }
    }

    @AfterEach
    fun tearDown() = server.close()

    private fun client() = HTTPClient.create(server.config(sleeper))

    @Test
    fun `GET com 503 transitorio e repetido`() = runBlocking {
        server.enqueue(MockResponse.Builder().code(503).build())
        server.enqueue(ok("voltou"))
        assertEquals(Eco("voltou"), client().send(ecoEndpoint()))
        assertEquals(2, server.requestCount)
        assertEquals(1, sleeper.sleeps.size)
    }

    @ParameterizedTest
    @EnumSource(HttpMethod::class, names = ["POST", "PATCH"])
    fun `metodo nao idempotente nunca e repetido`(method: HttpMethod) = runBlocking {
        server.enqueue(MockResponse.Builder().code(503).build())
        server.enqueue(MockResponse.Builder().code(204).build())
        val error = assertThrows<NetworkError.Http> { client().send(semCorpo("/v1/seguindo/x", method)) }
        assertEquals(503, error.status)
        assertEquals(1, server.requestCount)
        assertTrue(sleeper.sleeps.isEmpty())
    }

    @Test
    fun `DELETE e idempotente e pode repetir`() = runBlocking {
        server.enqueue(MockResponse.Builder().code(503).build())
        server.enqueue(MockResponse.Builder().code(204).build())
        client().send(semCorpo("/v1/seguindo/x", HttpMethod.DELETE))
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `respeita Retry-After em segundos`() = runBlocking {
        server.enqueue(MockResponse.Builder().code(429).addHeader("Retry-After", "3").build())
        server.enqueue(ok())
        client().send(ecoEndpoint())
        assertEquals(listOf(3.seconds), sleeper.sleeps.toList())
    }

    @Test
    fun `Retry-After acima do teto devolve a resposta sem esperar`() = runBlocking {
        server.enqueue(MockResponse.Builder().code(429).addHeader("Retry-After", "600").build())
        val error = assertThrows<NetworkError.Http> { client().send(ecoEndpoint()) }
        assertEquals(429, error.status)
        assertEquals(1, server.requestCount)
        assertTrue(sleeper.sleeps.isEmpty())
    }

    @Test
    fun `desiste depois do maximo de tentativas`() = runBlocking {
        repeat(3) { server.enqueue(MockResponse.Builder().code(502).build()) }
        val error = assertThrows<NetworkError.Http> { client().send(ecoEndpoint()) }
        assertEquals(502, error.status)
        assertEquals(3, server.requestCount) // 1 + maxRetries(2)
        assertEquals(2, sleeper.sleeps.size)
    }

    @Test
    fun `status nao transitorio nao repete`() = runBlocking {
        server.enqueue(MockResponse.Builder().code(404).build())
        assertThrows<NetworkError.Http> { client().send(ecoEndpoint()) }
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `queda de conexao em GET e repetida`() = runBlocking {
        server.enqueue(MockResponse.Builder().onResponseStart(SocketEffect.CloseSocket()).build())
        server.enqueue(ok("depois"))
        assertEquals(Eco("depois"), client().send(ecoEndpoint()))
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `queda de conexao em POST nao e repetida`() = runBlocking {
        server.enqueue(MockResponse.Builder().onResponseStart(SocketEffect.CloseSocket()).build())
        server.enqueue(MockResponse.Builder().code(204).build())
        assertThrows<NetworkError> { client().send(semCorpo("/v1/seguindo/x", HttpMethod.POST)) }
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `backoff exponencial com jitter fica dentro dos limites`() {
        val policy = RetryPolicy(baseDelay = 300.milliseconds, maxDelay = 1.seconds)
        val random = Random(7)
        repeat(50) {
            assertTrue(policy.backoff(0, random) in 150.milliseconds..300.milliseconds)
            assertTrue(policy.backoff(1, random) in 300.milliseconds..600.milliseconds)
            assertTrue(policy.backoff(5, random) in 500.milliseconds..1.seconds)
        }
        val distintos = (1..20).map { policy.backoff(1, random) }.toSet()
        assertTrue(distintos.size > 1, "há jitter")
    }

    @Test
    fun `Retry-After aceita data HTTP`() {
        val now = Instant.parse("2027-03-12T18:40:00Z")
        val headers = headersOf("Retry-After", "Fri, 12 Mar 2027 18:40:05 GMT")
        assertEquals(5.seconds, RetryAfter.parse(headers, now))
        assertEquals(null, RetryAfter.parse(headersOf("Retry-After", "lixo"), now))
        assertEquals(null, RetryAfter.parse(headersOf(), now))
    }
}

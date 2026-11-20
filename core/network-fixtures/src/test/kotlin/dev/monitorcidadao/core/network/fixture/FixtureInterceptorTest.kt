package dev.monitorcidadao.core.network.fixture

import dev.monitorcidadao.core.network.HTTPClient
import dev.monitorcidadao.core.network.HTTPClientConfig
import dev.monitorcidadao.core.network.HttpEndpoint
import dev.monitorcidadao.core.network.HttpMethod
import dev.monitorcidadao.core.network.NetworkError
import dev.monitorcidadao.core.network.interceptor.RetryPolicy
import dev.monitorcidadao.core.testing.Fixtures
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.time.Duration.Companion.milliseconds

class FixtureInterceptorTest {
    private val base = "https://api.monitorcidadao.invalid"

    @Nested
    inner class Rotas {
        private fun route(method: String, path: String) = FixtureRouter.route(method, "$base$path".toHttpUrl())

        @Test
        fun `rotas do api md`() {
            assertEquals(FixtureResponse.Json("seguindo.json"), route("GET", "/v1/seguindo"))
            assertEquals(FixtureResponse.Json("pessoa_cam-1001.json"), route("GET", "/v1/pessoas/cam-1001"))
            assertEquals(FixtureResponse.Json("posts_cam-1001_p1.json"), route("GET", "/v1/pessoas/cam-1001/posts"))
            assertEquals(FixtureResponse.Json("posts_sen-2001_p3.json"), route("GET", "/v1/pessoas/sen-2001/posts?cursor=p3"))
            assertEquals(FixtureResponse.NoContent, route("POST", "/v1/seguindo/cam-1001"))
            assertEquals(FixtureResponse.NoContent, route("DELETE", "/v1/seguindo/cam-1001"))
            assertEquals(FixtureResponse.NotFound, route("GET", "/v2/qualquer"))
            assertEquals(FixtureResponse.NotFound, route("PUT", "/v1/seguindo/cam-1001"))
        }

        @Test
        fun `todo arquivo apontado pelas rotas existe nas fixtures`() {
            listOf("cam-1001", "cam-1002", "sen-2001").forEach { id ->
                listOf("pessoa_$id.json", "posts_${id}_p1.json", "posts_${id}_p2.json", "posts_${id}_p3.json")
                    .forEach { assertTrue(Fixtures.readOrNull(it) != null, "fixture $it") }
            }
        }
    }

    @Serializable
    data class Pagina(val itens: List<JsonObject>, val proximo_cursor: String? = null)

    private val latencySleeper = RecordingSleeper()
    private val retrySleeper = RecordingSleeper()
    private val settings = FixtureSettings()

    private fun client(retry: RetryPolicy? = RetryPolicy()) = HTTPClient.create(
        HTTPClientConfig(
            baseUrl = base,
            userAgent = "teste",
            retryPolicy = retry,
            retrySleeper = retrySleeper,
            extraInterceptors = listOf(
                FixtureInterceptor(FixtureSource { Fixtures.readOrNull(it) }, settings, latencySleeper, kotlin.random.Random(1)),
            ),
        ),
    )

    @Test
    fun `serve as paginas pela pilha real com latencia`() = runBlocking {
        val endpoint = { cursor: String? ->
            HttpEndpoint("/v1/pessoas/cam-1001/posts", Pagina.serializer(), query = mapOf("cursor" to cursor))
        }
        val client = client()
        val p1 = client.send(endpoint(null))
        val p2 = client.send(endpoint(p1.proximo_cursor))
        val p3 = client.send(endpoint(p2.proximo_cursor))

        assertEquals(listOf("p2", "p3", null), listOf(p1.proximo_cursor, p2.proximo_cursor, p3.proximo_cursor))
        assertEquals(3, latencySleeper.sleeps.size)
        assertTrue(latencySleeper.sleeps.all { it in 400.milliseconds..900.milliseconds })
    }

    @Test
    fun `seguir responde 204`() = runBlocking {
        client().send(semCorpo("/v1/seguindo/cam-1001", HttpMethod.POST))
    }

    @Test
    fun `fail=1 responde 503 com corpo de erro`() = runBlocking {
        val error = assertThrows<NetworkError.Http> {
            client().send(HttpEndpoint("/v1/seguindo/cam-1001", kotlinx.serialization.serializer<Unit>(), HttpMethod.POST, query = mapOf("fail" to "1")))
        }
        assertEquals(503, error.status)
        assertEquals("indisponivel", error.apiError?.codigo)
        assertTrue(retrySleeper.sleeps.isEmpty(), "POST não repete")
    }

    @Test
    fun `modo falha passa pelo retry de verdade em GET`() = runBlocking {
        settings.failureMode = true
        val error = assertThrows<NetworkError.Http> { client().send(ecoEndpoint("/v1/seguindo")) }
        assertEquals(503, error.status)
        assertEquals(2, retrySleeper.sleeps.size)
        assertEquals(3, latencySleeper.sleeps.size)
    }

    @Test
    fun `pessoa inexistente responde 404`() = runBlocking {
        val error = assertThrows<NetworkError.Http> { client(retry = null).send(ecoEndpoint("/v1/pessoas/nao-existe")) }
        assertEquals(404, error.status)
        assertEquals("nao_encontrado", error.apiError?.codigo)
    }
}

package dev.monitorcidadao.data.api

import dev.monitorcidadao.core.network.HTTPClient
import dev.monitorcidadao.core.network.HTTPClientConfig
import dev.monitorcidadao.core.network.NetworkError
import dev.monitorcidadao.core.network.Priority
import dev.monitorcidadao.core.testing.Fixtures
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class EndpointsTest {
    private val server = MockWebServer()
    private lateinit var http: HTTPClient

    @BeforeEach
    fun setUp() {
        server.start()
        http = HTTPClient.create(
            HTTPClientConfig(baseUrl = server.url("/").toString(), userAgent = "Teste/1.0", retryPolicy = null),
        )
    }

    @AfterEach
    fun tearDown() = server.close()

    @Test
    fun `posts com cursor vao na query e a primeira pagina sem cursor`() = runTest {
        server.enqueue(MockResponse.Builder().body(Fixtures.read("posts_cam-1001_p1.json")).build())
        server.enqueue(MockResponse.Builder().body(Fixtures.read("posts_cam-1001_p2.json")).build())

        val p1 = http.send(Endpoints.postsDaPessoa("cam-1001"))
        val p2 = http.send(Endpoints.postsDaPessoa("cam-1001", cursor = p1.proximoCursor))

        assertEquals("/v1/pessoas/cam-1001/posts", server.takeRequest().target)
        assertEquals("/v1/pessoas/cam-1001/posts?cursor=p2", server.takeRequest().target)
        assertEquals("p3", p2.proximoCursor)
    }

    @Test
    fun `prioridade da primeira pagina e alta e das seguintes utilidade`() {
        assertEquals(Priority.Alta, Endpoints.postsDaPessoa("x").priority)
        assertEquals(Priority.Utilidade, Endpoints.postsDaPessoa("x", cursor = "p2").priority)
    }

    @Test
    fun `seguir e deixar de seguir aceitam 204 sem corpo`() = runTest {
        server.enqueue(MockResponse.Builder().code(204).build())
        server.enqueue(MockResponse.Builder().code(204).build())

        http.send(Endpoints.seguir("cam-1001"))
        http.send(Endpoints.deixarDeSeguir("cam-1001"))

        assertEquals("POST", server.takeRequest().method)
        assertEquals("DELETE", server.takeRequest().method)
    }

    @Test
    fun `503 com corpo de erro do contrato vira NetworkError Http`() = runTest {
        server.enqueue(
            MockResponse.Builder().code(503).body("""{"erro":{"codigo":"indisponivel","mensagem":"Tente mais tarde"}}""").build(),
        )

        val erro = assertThrows<NetworkError.Http> { http.send(Endpoints.seguir("cam-1001")) }

        assertEquals(503, erro.status)
        assertEquals("indisponivel", erro.apiError?.codigo)
    }

    @Test
    fun `pessoa decodifica pela pilha de rede`() = runTest {
        server.enqueue(MockResponse.Builder().body(Fixtures.read("pessoa_sen-2001.json")).build())

        val pessoa = http.send(Endpoints.pessoa("sen-2001"))

        assertEquals("senadora", pessoa.cargo)
        assertNull(server.takeRequest().headers["Cache-Control"])
    }

    @Test
    fun `id com barra, interrogacao ou acento vai codificado num segmento so`() = runTest {
        server.enqueue(MockResponse.Builder().code(204).build())
        server.enqueue(MockResponse.Builder().code(204).build())

        http.send(Endpoints.seguir("../pessoas/x?y#z"))
        http.send(Endpoints.deixarDeSeguir("ção 1"))

        assertEquals("/v1/seguindo/..%2Fpessoas%2Fx%3Fy%23z", server.takeRequest().target)
        assertEquals("/v1/seguindo/%C3%A7%C3%A3o%201", server.takeRequest().target)
    }

    @Test
    fun `id que a URL resolveria como diretorio e recusado`() {
        listOf("", ".", "..").forEach { id ->
            assertThrows<IdInvalidoException> { Endpoints.pessoa(id) }
        }
        assertEquals("/v1/pessoas/cam-1001/posts", Endpoints.postsDaPessoa("cam-1001").path)
    }
}

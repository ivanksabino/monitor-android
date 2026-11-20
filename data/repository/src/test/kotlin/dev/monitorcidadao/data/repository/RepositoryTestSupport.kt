package dev.monitorcidadao.data.repository

import dev.monitorcidadao.core.network.HTTPClient
import dev.monitorcidadao.core.network.HTTPClientConfig
import dev.monitorcidadao.core.network.fixture.FixtureResponse
import dev.monitorcidadao.core.network.fixture.FixtureRouter
import dev.monitorcidadao.core.testing.Fixtures
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * MockWebServer que responde as rotas do api.md com as fixtures reais (mesmo `FixtureRouter` do
 * servidor falso do Debug). [falharCom] força um status para todas as chamadas; [segurar]
 * prende as respostas até o teste liberar.
 */
class FixtureDispatcher : Dispatcher() {
    @Volatile var falharCom: Int? = null
    @Volatile var segurar: CountDownLatch? = null
    val pedidos = CopyOnWriteArrayList<String>()

    override fun dispatch(request: RecordedRequest): MockResponse {
        pedidos += "${request.method} ${request.target}"
        segurar?.await(10, TimeUnit.SECONDS)
        falharCom?.let { status ->
            return MockResponse.Builder().code(status)
                .body("""{"erro":{"codigo":"teste","mensagem":"falha simulada"}}""").build()
        }
        return when (val r = FixtureRouter.route(request.method, request.url)) {
            is FixtureResponse.Json -> Fixtures.readOrNull(r.file)
                ?.let { MockResponse.Builder().body(it).build() }
                ?: MockResponse.Builder().code(404).body("""{"erro":{"codigo":"nao_encontrado","mensagem":"sem fixture"}}""").build()
            FixtureResponse.NoContent -> MockResponse.Builder().code(204).build()
            FixtureResponse.NotFound -> MockResponse.Builder().code(404).build()
        }
    }
}

fun MockWebServer.httpClient(): HTTPClient = HTTPClient.create(
    HTTPClientConfig(baseUrl = url("/").toString(), userAgent = "MonitorCidadao-Teste/1.0", retryPolicy = null),
)

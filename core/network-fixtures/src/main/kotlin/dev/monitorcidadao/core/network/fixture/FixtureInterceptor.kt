package dev.monitorcidadao.core.network.fixture

import dev.monitorcidadao.core.network.interceptor.Sleeper
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import kotlin.random.Random
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/** Ajustes do servidor de fixtures. Mutável em runtime (menu de debug / flag). */
class FixtureSettings(
    latency: ClosedRange<Duration> = 400.milliseconds..900.milliseconds,
    failureMode: Boolean = false,
) {
    @Volatile var latency: ClosedRange<Duration> = latency

    /** Ligado: toda chamada responde 503 (equivale a `?fail=1`). */
    @Volatile var failureMode: Boolean = failureMode
}

/**
 * Servidor falso **dentro da pilha real de rede** (só Debug): responde as rotas do api.md com os
 * JSONs de `contracts/fixtures`, com latência artificial e modo falha (`?fail=1` ou [FixtureSettings.failureMode]).
 * Fica no fim da cadeia de interceptors de aplicação, então headers, retry e log rodam de verdade.
 */
class FixtureInterceptor(
    private val source: FixtureSource,
    private val settings: FixtureSettings = FixtureSettings(),
    private val sleeper: Sleeper = Sleeper.Real,
    private val random: Random = Random.Default,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        sleeper.sleep(randomLatency(), chain.call())

        if (settings.failureMode || request.url.queryParameter("fail") == "1") {
            return request.error(503, "indisponivel", "Serviço indisponível (modo falha das fixtures).")
        }
        return when (val route = FixtureRouter.route(request.method, request.url)) {
            FixtureResponse.NoContent -> request.respond(204, null)
            FixtureResponse.NotFound -> request.error(404, "nao_encontrado", "Rota sem fixture.")
            is FixtureResponse.Json -> source.read(route.file)
                ?.let { request.respond(200, it, route.file) }
                ?: request.error(404, "nao_encontrado", "Fixture ${route.file} não encontrada.")
        }
    }

    private fun randomLatency(): Duration {
        val range = settings.latency
        val min = range.start.inWholeMilliseconds
        val max = range.endInclusive.inWholeMilliseconds
        return (if (max > min) random.nextLong(min, max + 1) else min).milliseconds
    }

    private fun Request.error(code: Int, codigo: String, mensagem: String): Response =
        respond(code, """{"erro":{"codigo":"$codigo","mensagem":"$mensagem"}}""")

    private fun Request.respond(code: Int, json: String?, fixture: String? = null): Response =
        Response.Builder()
            .request(this)
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message(if (code < 300) "OK" else "Fixture")
            .apply { fixture?.let { header("X-Fixture", it) } }
            .body((json ?: "").toResponseBody(JSON))
            .build()

    private companion object {
        val JSON = "application/json; charset=utf-8".toMediaType()
    }
}

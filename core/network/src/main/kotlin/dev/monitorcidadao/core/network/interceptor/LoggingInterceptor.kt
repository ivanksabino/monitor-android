package dev.monitorcidadao.core.network.interceptor

import dev.monitorcidadao.core.network.Priority
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException

fun interface HttpLogger {
    fun log(message: String)
}

/** Log enxuto: método, URL, prioridade, status e duração. Nunca registra token nem corpo. */
class LoggingInterceptor(
    private val logger: HttpLogger,
    private val clock: () -> Long = System::nanoTime,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val priority = request.tag(Priority::class.java)?.name ?: "-"
        logger.log("→ ${request.method} ${request.url} [$priority]")
        val start = clock()
        val response = try {
            chain.proceed(request)
        } catch (e: IOException) {
            logger.log("✕ ${request.method} ${request.url} ${e::class.simpleName}: ${e.message}")
            throw e
        }
        val tookMs = (clock() - start) / 1_000_000
        logger.log("← ${response.code} ${request.method} ${request.url} (${tookMs} ms)")
        return response
    }
}

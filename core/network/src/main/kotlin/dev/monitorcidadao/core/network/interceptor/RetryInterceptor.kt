package dev.monitorcidadao.core.network.interceptor

import dev.monitorcidadao.core.network.HttpMethod
import dev.monitorcidadao.core.network.Priority
import dev.monitorcidadao.core.network.RetryAfter
import dev.monitorcidadao.core.network.hasTlsFailure
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException
import java.time.Instant
import kotlin.random.Random
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

data class RetryPolicy(
    val maxRetries: Int = 2,
    val baseDelay: Duration = 300.milliseconds,
    val maxDelay: Duration = 8.seconds,
    /**
     * Maior `Retry-After` que se espera DENTRO da fila. A espera segura a vaga do host no
     * `RequestScheduler`, então o teto é curto; acima dele a resposta volta a quem chamou
     * (`NetworkError.Http.retryAfter` → `AppError.MuitasRequisicoes`).
     */
    val maxRetryAfter: Duration = 5.seconds,
    /**
     * Teto para [Priority.Fundo]: ninguém espera por ela na tela, então ela não segura vaga
     * esperando `Retry-After` (devolve a resposta e o sync reagenda).
     */
    val maxRetryAfterFundo: Duration = Duration.ZERO,
    val retryableStatus: Set<Int> = setOf(408, 429, 500, 502, 503, 504),
) {
    fun retryAfterCap(priority: Priority?): Duration =
        if (priority == Priority.Fundo) maxRetryAfterFundo else maxRetryAfter

    /** Backoff exponencial com jitter ("equal jitter"): metade fixa + metade aleatória. */
    fun backoff(attempt: Int, random: Random): Duration {
        val exponential = (baseDelay.inWholeMilliseconds shl attempt.coerceAtMost(20))
            .coerceAtMost(maxDelay.inWholeMilliseconds)
        val half = exponential / 2
        return (half + random.nextLong(0, half + 1)).milliseconds
    }
}

/**
 * Repete só métodos idempotentes (GET, HEAD, PUT, DELETE, OPTIONS), em falha de I/O transitória
 * ou status de [RetryPolicy.retryableStatus], respeitando `Retry-After`.
 */
class RetryInterceptor(
    private val policy: RetryPolicy = RetryPolicy(),
    private val random: Random = Random.Default,
    private val sleeper: Sleeper = Sleeper.Real,
    private val now: () -> Instant = Instant::now,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val idempotent = HttpMethod.entries.firstOrNull { it.name == request.method }?.isIdempotent ?: false
        if (!idempotent) return chain.proceed(request)

        val retryAfterCap = policy.retryAfterCap(request.tag(Priority::class.java))
        var attempt = 0
        while (true) {
            val delay: Duration = try {
                val response = chain.proceed(request)
                if (response.code !in policy.retryableStatus || attempt >= policy.maxRetries) return response
                val retryAfter = RetryAfter.parse(response.headers, now())
                if (retryAfter != null && retryAfter > retryAfterCap) return response
                response.close()
                retryAfter ?: policy.backoff(attempt, random)
            } catch (e: IOException) {
                if (chain.call().isCanceled() || !e.isTransient() || attempt >= policy.maxRetries) throw e
                policy.backoff(attempt, random)
            }
            sleeper.sleep(delay, chain.call())
            attempt++
        }
    }

    /** Recusa de TLS/pinning/mTLS nunca é transitória, nem quando o OkHttp a esconde num `ConnectException`. */
    private fun IOException.isTransient(): Boolean = !hasTlsFailure() && message != "Canceled"
}

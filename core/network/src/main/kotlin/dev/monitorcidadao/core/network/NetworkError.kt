package dev.monitorcidadao.core.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.io.IOException
import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException
import kotlin.time.Duration

/** Corpo de erro do contrato: `{ "erro": { "codigo": "...", "mensagem": "..." } }`. */
@Serializable
data class ApiError(
    @SerialName("codigo") val codigo: String,
    @SerialName("mensagem") val mensagem: String,
)

@Serializable
internal data class ApiErrorEnvelope(@SerialName("erro") val erro: ApiError)

/**
 * Erros tipados da camada de rede. Cancelamento de coroutine NÃO vira NetworkError: propaga como `CancellationException`.
 *
 * De-para com o `NetworkError` do iOS (mesmos casos, nomes idiomáticos de cada lado):
 *
 * | Android          | iOS                     |
 * |------------------|-------------------------|
 * | [NoConnection]   | `semRede`               |
 * | [Timeout]        | `timeout`               |
 * | [Http]           | `http(HTTPFailure)`     |
 * | [Decoding]       | `decodificacao`         |
 * | [Cancelled]      | `cancelado`             |
 * | [Security]       | `segurancaTransporte`   |
 * | [Unknown]        | `transporte`            |
 * | (não existe)     | `urlInvalida`: no Android a URL base é validada ao montar o `HTTPClient` |
 *
 * O `:data:repository` traduz para `AppError` (`toAppError()`), igual ao módulo Data do iOS.
 */
sealed class NetworkError(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class NoConnection(cause: Throwable? = null) : NetworkError("Sem conexão", cause)

    class Timeout(cause: Throwable? = null) : NetworkError("Tempo esgotado", cause)

    class Http(
        val status: Int,
        val apiError: ApiError? = null,
        val retryAfter: Duration? = null,
    ) : NetworkError("HTTP $status${apiError?.let { " (${it.codigo})" }.orEmpty()}")

    class Decoding(cause: Throwable) : NetworkError("Falha ao decodificar resposta", cause)

    /** Chamada HTTP cancelada por fora da coroutine (ex.: cliente encerrado). */
    class Cancelled(cause: Throwable? = null) : NetworkError("Cancelada", cause)

    /** TLS, pinning ou mTLS recusados. */
    class Security(cause: Throwable) : NetworkError("Falha de segurança de transporte", cause)

    /** Outra falha de transporte (equivale a `transporte` no iOS). */
    class Unknown(cause: Throwable) : NetworkError("Erro de rede inesperado", cause)
}

internal fun Throwable.toNetworkError(callCanceled: Boolean = false): NetworkError = when {
    this is NetworkError -> this
    this is TokenRefreshException -> cause.toRefreshFailure()
    // `callTimeout` também marca a Call como cancelada: o tempo esgotado vem antes do cancelamento.
    this is SocketTimeoutException -> NetworkError.Timeout(this)
    this is InterruptedIOException && message == "timeout" -> NetworkError.Timeout(this)
    callCanceled -> NetworkError.Cancelled(this)
    hasTlsFailure() -> NetworkError.Security(this)
    this is UnknownHostException || this is ConnectException || this is NoRouteToHostException ->
        NetworkError.NoConnection(this)
    this is IOException && message == "Canceled" -> NetworkError.Cancelled(this)
    this is IOException -> NetworkError.NoConnection(this)
    else -> NetworkError.Unknown(this)
}

/**
 * O OkHttp tenta várias rotas (IPv6/IPv4) e reporta a última falha, guardando as outras em
 * `suppressed`: uma recusa de pinning/TLS pode vir escondida atrás de um `ConnectException`.
 */
internal fun Throwable.hasTlsFailure(seen: MutableSet<Throwable> = mutableSetOf()): Boolean {
    if (!seen.add(this)) return false
    if (this is SSLException) return true
    return suppressed.any { it.hasTlsFailure(seen) } || (cause?.hasTlsFailure(seen) ?: false)
}

/**
 * Falha da renovação de token dentro do `AuthInterceptor`. É `IOException` para o OkHttp tratar
 * como falha da chamada (qualquer outra exceção num interceptor derruba a thread do Dispatcher).
 */
internal class TokenRefreshException(override val cause: Throwable) : IOException("Falha ao renovar token", cause)

private fun Throwable.toRefreshFailure(): NetworkError = when (this) {
    is NetworkError -> this
    is kotlinx.coroutines.TimeoutCancellationException -> NetworkError.Timeout(this)
    is IOException -> toNetworkError()
    else -> NetworkError.Unknown(this)
}

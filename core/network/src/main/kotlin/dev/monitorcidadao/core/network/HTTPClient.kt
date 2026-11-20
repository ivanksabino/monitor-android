package dev.monitorcidadao.core.network

import dev.monitorcidadao.core.network.auth.AccessTokenStore
import dev.monitorcidadao.core.network.auth.AuthInterceptor
import dev.monitorcidadao.core.network.auth.InMemoryAccessTokenStore
import dev.monitorcidadao.core.network.auth.SingleFlightTokenRefresher
import dev.monitorcidadao.core.network.auth.TokenRefresher
import dev.monitorcidadao.core.network.interceptor.HeadersInterceptor
import dev.monitorcidadao.core.network.interceptor.HttpLogger
import dev.monitorcidadao.core.network.interceptor.LoggingInterceptor
import dev.monitorcidadao.core.network.interceptor.RetryInterceptor
import dev.monitorcidadao.core.network.interceptor.RetryPolicy
import dev.monitorcidadao.core.network.interceptor.Sleeper
import dev.monitorcidadao.core.network.security.ClientIdentityResolver
import dev.monitorcidadao.core.network.security.TransportSecurity
import dev.monitorcidadao.core.network.security.applyTransportSecurity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import okhttp3.CacheControl
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Dispatcher
import okhttp3.EventListener
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.net.ssl.X509TrustManager
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.random.Random
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** Tudo que monta o [HTTPClient]. O `AppContainer` do MVP usa [TransportSecurity.Default]. */
data class HTTPClientConfig(
    val baseUrl: String,
    val userAgent: String,
    val transportSecurity: TransportSecurity = TransportSecurity.Default,
    val identityResolver: ClientIdentityResolver? = null,
    val tokenStore: AccessTokenStore = InMemoryAccessTokenStore(),
    val tokenRefresher: TokenRefresher = TokenRefresher.None,
    val retryPolicy: RetryPolicy? = RetryPolicy(),
    /** Espera do backoff (teste injeta uma que não dorme). */
    val retrySleeper: Sleeper = Sleeper.Real,
    val random: Random = Random.Default,
    val logger: HttpLogger? = null,
    /** Vão para o fim da cadeia de aplicação (ex.: `FixtureInterceptor` no Debug). */
    val extraInterceptors: List<Interceptor> = emptyList(),
    val maxConcurrentPerHost: Int = RequestScheduler.DEFAULT_MAX_PER_HOST,
    val connectTimeout: Duration = 15.seconds,
    val readTimeout: Duration = 20.seconds,
    /** Teto da chamada inteira (tentativas + backoff + `Retry-After`), para a tela visível não esperar mais que isso. */
    val callTimeout: Duration = 30.seconds,
    /** Teto da renovação de token (um refresh pendurado não segura o mutex para sempre). */
    val tokenRefreshTimeout: Duration = 15.seconds,
    /** Só para teste: confiar numa CA própria. */
    val trustManager: X509TrustManager? = null,
    /** Diagnóstico/teste (ex.: observar `canceled`). */
    val eventListenerFactory: EventListener.Factory? = null,
)

/** Resposta crua já lida por inteiro (compartilhável entre chamadas deduplicadas). */
class RawResponse(val code: Int, val headers: Headers, val body: ByteArray) {
    val isSuccessful: Boolean get() = code in 200..299
}

/**
 * Executa [Endpoint]s: monta o request, agenda no [RequestScheduler] (prioridade, teto por host,
 * dedup de GET), executa no OkHttp com cancelamento propagado e decodifica com kotlinx.serialization.
 *
 * Cache HTTP: não há `okhttp3.Cache` configurado no MVP (o cache do app é o SQLite). [CachePolicy]
 * só controla o `Cache-Control` enviado (caches intermediários) e se a GET pode ser deduplicada.
 */
class HTTPClient(
    baseUrl: String,
    val okHttpClient: OkHttpClient,
    internal val scheduler: RequestScheduler = RequestScheduler(),
    private val json: Json = NetworkJson,
) {
    private val baseUrl: HttpUrl = baseUrl.toHttpUrl()

    private val imageClient: OkHttpClient by lazy { okHttpClient.isolated() }

    /**
     * Cliente das imagens (Coil): mesmo pool de conexões e mesma `TransportSecurity` da API, mas
     * sem nenhum interceptor de aplicação (sem `Accept: application/json`, sem `Authorization`,
     * sem retry, sem fixtures) e com `Dispatcher` próprio, para não disputar as vagas por host
     * das chamadas de tela.
     */
    fun imageCallFactory(): Call.Factory = imageClient

    suspend fun <T> send(endpoint: Endpoint<T>, priority: Priority = endpoint.priority): T {
        val request = buildRequest(endpoint, priority)
        val dedupKey = dedupKey(endpoint, request)
        val raw = try {
            scheduler.execute(request.url.host, priority, dedupKey) { okHttpClient.newCall(request).await() }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            throw e.toNetworkError()
        }
        return decode(endpoint.responseSerializer, raw)
    }

    /**
     * GET idêntica em voo vira uma só. Pedido que exige rede (`NoCache`/`NoStore`, ex.: puxar para
     * atualizar) nunca pega carona numa chamada já em voo; os headers do endpoint entram na chave.
     */
    private fun dedupKey(endpoint: Endpoint<*>, request: Request): String? {
        if (endpoint.method != HttpMethod.GET || endpoint.cachePolicy != CachePolicy.Default) return null
        val headers = endpoint.headers.entries.sortedBy { it.key.lowercase() }
            .joinToString("|") { "${it.key.lowercase()}=${it.value}" }
        return "GET ${request.url} [$headers]"
    }

    internal fun buildRequest(endpoint: Endpoint<*>, priority: Priority): Request {
        val url = baseUrl.newBuilder()
            .addEncodedPathSegments(endpoint.path.trimStart('/'))
            .apply { endpoint.query.forEach { (k, v) -> if (v != null) addQueryParameter(k, v) } }
            .build()
        val body = endpoint.body?.let { it.content.toRequestBody(it.contentType.toMediaType()) }
        return Request.Builder()
            .url(url)
            .method(endpoint.method.name, body ?: if (endpoint.method.requiresBody) EMPTY_BODY else null)
            .apply { endpoint.headers.forEach { (k, v) -> header(k, v) } }
            .apply { cacheControl(endpoint.cachePolicy)?.let(::cacheControl) }
            .tag(Priority::class.java, priority)
            .build()
    }

    private fun <T> decode(serializer: KSerializer<T>, raw: RawResponse): T {
        if (!raw.isSuccessful) {
            throw NetworkError.Http(
                status = raw.code,
                apiError = decodeApiError(raw.body),
                retryAfter = RetryAfter.parse(raw.headers),
            )
        }
        @Suppress("UNCHECKED_CAST")
        if (serializer == Unit.serializer()) return Unit as T
        return try {
            json.decodeFromString(serializer, raw.body.decodeToString())
        } catch (e: SerializationException) {
            throw NetworkError.Decoding(e)
        } catch (e: IllegalArgumentException) {
            throw NetworkError.Decoding(e)
        }
    }

    private fun decodeApiError(body: ByteArray): ApiError? =
        runCatching { json.decodeFromString(ApiErrorEnvelope.serializer(), body.decodeToString()).erro }.getOrNull()

    companion object {
        private val EMPTY_BODY = ByteArray(0).toRequestBody(null)

        private val HttpMethod.requiresBody: Boolean
            get() = this == HttpMethod.POST || this == HttpMethod.PUT || this == HttpMethod.PATCH

        private fun cacheControl(policy: CachePolicy): CacheControl? = when (policy) {
            CachePolicy.Default -> null
            CachePolicy.NoCache -> CacheControl.FORCE_NETWORK
            CachePolicy.NoStore -> CacheControl.Builder().noStore().noCache().build()
        }

        /**
         * Monta um OkHttp base (pool, timeouts, redirects, segurança) e deriva dele:
         * - o cliente da API, com a cadeia (headers → token → retry → log → extras) e o Dispatcher
         *   com o teto por host;
         * - o cliente da renovação de token e o das imagens, sem interceptors e com Dispatcher próprio.
         */
        fun create(config: HTTPClientConfig): HTTPClient {
            val scheduler = RequestScheduler(config.maxConcurrentPerHost)
            val base = OkHttpClient.Builder()
                .connectTimeout(config.connectTimeout.inWholeMilliseconds, TimeUnit.MILLISECONDS)
                .readTimeout(config.readTimeout.inWholeMilliseconds, TimeUnit.MILLISECONDS)
                .callTimeout(config.callTimeout.inWholeMilliseconds, TimeUnit.MILLISECONDS)
                .retryOnConnectionFailure(false) // a política de retry é nossa (só idempotente)
                .followSslRedirects(false) // nunca de https para http
                .apply { config.eventListenerFactory?.let(::eventListenerFactory) }
                .applyTransportSecurity(config.transportSecurity, config.identityResolver, config.trustManager)
                .build()
            val refresher = SingleFlightTokenRefresher(
                store = config.tokenStore,
                refresher = config.tokenRefresher,
                client = base.isolated(),
                timeout = config.tokenRefreshTimeout,
            )
            val okHttp = base.newBuilder()
                .dispatcher(
                    Dispatcher().apply {
                        maxRequestsPerHost = config.maxConcurrentPerHost
                    },
                )
                .addInterceptor(HeadersInterceptor(config.userAgent))
                .addInterceptor(AuthInterceptor(config.tokenStore, refresher))
                .apply { config.retryPolicy?.let { addInterceptor(RetryInterceptor(it, config.random, config.retrySleeper)) } }
                .apply { config.logger?.let { addInterceptor(LoggingInterceptor(it)) } }
                .apply { config.extraInterceptors.forEach(::addInterceptor) }
                .build()
            return HTTPClient(config.baseUrl, okHttp, scheduler)
        }
    }
}

/** Mesmo pool, timeouts e TLS; sem interceptors e com Dispatcher próprio (fora do teto por host da API). */
internal fun OkHttpClient.isolated(): OkHttpClient = newBuilder()
    .apply {
        interceptors().clear()
        networkInterceptors().clear()
    }
    .dispatcher(Dispatcher())
    .build()

/** Executa a chamada de forma suspensa; cancelar a coroutine cancela a `Call` do OkHttp. */
internal suspend fun Call.await(): RawResponse = suspendCancellableCoroutine { continuation ->
    continuation.invokeOnCancellation { cancel() }
    enqueue(
        object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (continuation.isActive) continuation.resumeWithException(e.toNetworkError(call.isCanceled()))
            }

            override fun onResponse(call: Call, response: Response) {
                val raw = try {
                    response.use { RawResponse(it.code, it.headers, it.body.bytes()) }
                } catch (e: IOException) {
                    if (continuation.isActive) continuation.resumeWithException(e.toNetworkError(call.isCanceled()))
                    return
                }
                if (continuation.isActive) continuation.resume(raw)
            }
        },
    )
}

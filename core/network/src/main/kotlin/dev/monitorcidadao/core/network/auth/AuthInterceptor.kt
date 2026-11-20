package dev.monitorcidadao.core.network.auth

import dev.monitorcidadao.core.network.TokenRefreshException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import okhttp3.Call
import okhttp3.Interceptor
import okhttp3.Response
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** Onde o token de acesso vive (memória + segredo persistido pelo app). */
interface AccessTokenStore {
    fun current(): String?
    fun update(token: String?)
}

class InMemoryAccessTokenStore(initial: String? = null) : AccessTokenStore {
    @Volatile private var token: String? = initial
    override fun current(): String? = token
    override fun update(token: String?) {
        this.token = token
    }
}

/**
 * Renova o token. `null` = sessão não pode ser renovada (o app trata como deslogado).
 *
 * **Contrato:** a renovação usa o [client] recebido. Ele é um OkHttp próprio da renovação:
 * mesmo pool e mesma `TransportSecurity` do app, mas **sem** os interceptors de aplicação
 * (sem `AuthInterceptor`, sem retry, sem fixtures), com `Dispatcher` separado e **fora** do
 * `RequestScheduler`. Não renove pelo `HTTPClient` da API: a chamada que recebeu 401 está
 * segurando uma vaga do host enquanto espera, e com o host lotado a renovação nunca sairia.
 * Lançar qualquer exceção é seguro: quem chamou recebe um `NetworkError` tipado.
 */
fun interface TokenRefresher {
    suspend fun refresh(client: Call.Factory): String?

    companion object {
        /** MVP sem login: não há o que renovar. */
        val None: TokenRefresher = TokenRefresher { null }
    }
}

/**
 * Renovação *single-flight*: vários 401 simultâneos geram **uma** renovação; quem chega
 * depois reaproveita o token novo em vez de renovar de novo. A renovação tem [timeout].
 */
class SingleFlightTokenRefresher(
    private val store: AccessTokenStore,
    private val refresher: TokenRefresher,
    private val client: Call.Factory,
    private val timeout: Duration = 15.seconds,
) {
    private val mutex = Mutex()

    suspend fun refreshAfter(staleToken: String?): String? = withTimeout(timeout) {
        mutex.withLock {
            val current = store.current()
            if (current != null && current != staleToken) return@withLock current
            refresher.refresh(client).also(store::update)
        }
    }
}

/** Põe `Authorization: Bearer` e, em 401, renova (single-flight) e repete **uma** vez. */
class AuthInterceptor(
    private val store: AccessTokenStore,
    private val refresher: SingleFlightTokenRefresher,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val sent = store.current()
        val response = chain.proceed(chain.request().withToken(sent))
        if (response.code != 401) return response

        val renewed = try {
            runBlocking { refresher.refreshAfter(sent) }
        } catch (e: Throwable) {
            // Só IOException pode sair de um interceptor: o resto derrubaria a thread do Dispatcher.
            response.close()
            throw TokenRefreshException(e)
        }
        if (renewed == null || renewed == sent) return response
        response.close()
        return chain.proceed(chain.request().withToken(renewed))
    }

    private fun okhttp3.Request.withToken(token: String?): okhttp3.Request =
        if (token == null) this else newBuilder().header("Authorization", "Bearer $token").build()
}

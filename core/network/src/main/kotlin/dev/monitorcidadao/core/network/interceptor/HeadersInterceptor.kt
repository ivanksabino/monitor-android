package dev.monitorcidadao.core.network.interceptor

import okhttp3.Interceptor
import okhttp3.Response

/** Cabeçalhos fixos de toda chamada. O token fica no [dev.monitorcidadao.core.network.auth.AuthInterceptor]. */
class HeadersInterceptor(
    private val userAgent: String,
    private val acceptLanguage: String = "pt-BR",
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        // Accept e Accept-Language só entram se o Endpoint não pediu outro valor.
        val request = original.newBuilder()
            .apply { if (original.header("Accept") == null) header("Accept", "application/json") }
            .apply { if (original.header("Accept-Language") == null) header("Accept-Language", acceptLanguage) }
            .header("User-Agent", userAgent)
            .build()
        return chain.proceed(request)
    }
}

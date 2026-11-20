package dev.monitorcidadao.app

import dev.monitorcidadao.core.network.HTTPClientConfig
import dev.monitorcidadao.core.network.interceptor.HttpLogger
import dev.monitorcidadao.core.network.security.TransportSecurity
import dev.monitorcidadao.data.api.ApiConfig
import okhttp3.Interceptor

/**
 * Configuração de rede do app, como função pura para o teste conferir o que vai para produção.
 * MVP: pinning e mTLS DESLIGADOS. Ligar = trocar [TransportSecurity.Default] aqui e passar um
 * `identityResolver` (ver contracts/arquitetura.md §5).
 */
internal fun appHttpClientConfig(
    versionName: String,
    logger: HttpLogger?,
    interceptors: List<Interceptor>,
): HTTPClientConfig = HTTPClientConfig(
    baseUrl = ApiConfig.BASE_URL,
    userAgent = "MonitorCidadao-Android/$versionName",
    transportSecurity = TransportSecurity.Default,
    identityResolver = null,
    logger = logger,
    extraInterceptors = interceptors,
)

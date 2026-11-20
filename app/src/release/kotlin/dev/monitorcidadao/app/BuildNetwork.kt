package dev.monitorcidadao.app

import android.content.Context
import dev.monitorcidadao.core.featureflags.FeatureFlags
import dev.monitorcidadao.core.network.interceptor.HttpLogger
import okhttp3.Interceptor

/** Release: sem fixtures e sem log de rede. */
@Suppress("UNUSED_PARAMETER")
internal class BuildNetwork(context: Context, flags: FeatureFlags) {
    val logger: HttpLogger? = null
    val interceptors: List<Interceptor> = emptyList()
}

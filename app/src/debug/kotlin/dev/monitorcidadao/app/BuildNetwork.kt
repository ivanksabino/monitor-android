package dev.monitorcidadao.app

import android.content.Context
import android.util.Log
import dev.monitorcidadao.core.featureflags.FeatureFlag
import dev.monitorcidadao.core.featureflags.FeatureFlags
import dev.monitorcidadao.core.network.fixture.AssetFixtureSource
import dev.monitorcidadao.core.network.fixture.FixtureInterceptor
import dev.monitorcidadao.core.network.fixture.FixtureSettings
import dev.monitorcidadao.core.network.interceptor.HttpLogger
import okhttp3.Interceptor

/** Debug: log de rede + servidor de fixtures (assets gerados de contracts/fixtures) dentro da pilha real. */
internal class BuildNetwork(context: Context, flags: FeatureFlags) {
    val fixtureSettings = FixtureSettings(failureMode = flags.isEnabled(FeatureFlag.FixtureModoFalha))

    val logger: HttpLogger = HttpLogger { Log.d("HTTP", it) }

    val interceptors: List<Interceptor> = listOf(
        FixtureInterceptor(AssetFixtureSource(context.assets), fixtureSettings),
    )
}

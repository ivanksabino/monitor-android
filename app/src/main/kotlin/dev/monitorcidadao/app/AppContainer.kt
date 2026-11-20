package dev.monitorcidadao.app

import android.content.Context
import android.util.Log
import dev.monitorcidadao.core.analytics.Analytics
import dev.monitorcidadao.core.analytics.AnalyticsSink
import dev.monitorcidadao.core.analytics.LogAnalyticsSink
import dev.monitorcidadao.core.database.MonitorDatabase
import dev.monitorcidadao.core.featureflags.FeatureFlags
import dev.monitorcidadao.core.featureflags.LocalFeatureFlags
import dev.monitorcidadao.core.imageloader.RemoteImageLoader
import dev.monitorcidadao.core.network.HTTPClient
import dev.monitorcidadao.core.storage.PreferencesStore
import dev.monitorcidadao.core.storage.SecretStore
import dev.monitorcidadao.core.storage.StorageFactory
import dev.monitorcidadao.data.repository.DefaultPessoaRepository
import dev.monitorcidadao.data.repository.DefaultPostsRepository
import dev.monitorcidadao.data.repository.DefaultSeguindoRepository
import dev.monitorcidadao.domain.repository.PessoaRepository
import dev.monitorcidadao.domain.repository.PostsRepository
import dev.monitorcidadao.domain.repository.SeguindoRepository
import dev.monitorcidadao.domain.usecase.ObterPerfil
import dev.monitorcidadao.domain.usecase.SeguirPessoa

/**
 * Raiz de composição manual: com o [AppFactory], o ÚNICO lugar que conhece implementações concretas.
 * Rede com `TransportSecurity.Default` (pinning e mTLS desligados); fixtures só no build Debug.
 */
class AppContainer(context: Context) : AppDependencies {
    private val appContext = context.applicationContext

    val featureFlags: FeatureFlags = LocalFeatureFlags()

    override val analytics: AnalyticsSink = Analytics(
        buildList {
            if (BuildConfig.DEBUG) add(LogAnalyticsSink { Log.d("Analytics", it) })
        },
    )

    val preferences: PreferencesStore by lazy { StorageFactory.preferences(appContext) }
    val secrets: SecretStore by lazy { StorageFactory.secrets(appContext) }

    private val buildNetwork = BuildNetwork(appContext, featureFlags)

    val httpClient: HTTPClient by lazy {
        HTTPClient.create(appHttpClientConfig(BuildConfig.VERSION_NAME, buildNetwork.logger, buildNetwork.interceptors))
    }

    val database: MonitorDatabase by lazy { MonitorDatabase.create(appContext) }

    // Data: as features só enxergam as interfaces do Domain.
    val pessoaRepository: PessoaRepository by lazy { DefaultPessoaRepository(httpClient, database.pessoaDao()) }
    override val postsRepository: PostsRepository by lazy { DefaultPostsRepository(httpClient, database.feedDao()) }
    override val seguindoRepository: SeguindoRepository by lazy {
        DefaultSeguindoRepository(httpClient, database.seguidoDao(), database.filaEnvioDao())
    }

    // Use cases (só onde há regra).
    override val obterPerfil: ObterPerfil by lazy { ObterPerfil(pessoaRepository) }

    /** Um só para o app inteiro: os locks dele (local e por pessoa) valem para os toques em Seguir de qualquer tela. */
    override val seguirPessoa: SeguirPessoa by lazy { SeguirPessoa(seguindoRepository) }

    init {
        // Imagens: cliente sem os interceptors da API (sem token, sem fixtures) e com Dispatcher próprio.
        RemoteImageLoader.install(appContext) { httpClient.imageCallFactory() }
    }
}

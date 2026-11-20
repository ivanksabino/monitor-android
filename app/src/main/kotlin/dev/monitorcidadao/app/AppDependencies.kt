package dev.monitorcidadao.app

import dev.monitorcidadao.core.analytics.AnalyticsSink
import dev.monitorcidadao.domain.repository.PostsRepository
import dev.monitorcidadao.domain.repository.SeguindoRepository
import dev.monitorcidadao.domain.usecase.ObterPerfil
import dev.monitorcidadao.domain.usecase.SeguirPessoa

/**
 * O que o [AppFactory] precisa para montar as telas. O [AppContainer] é a implementação real;
 * testes e previews passam dependências falsas.
 */
interface AppDependencies {
    val analytics: AnalyticsSink
    val obterPerfil: ObterPerfil
    val seguirPessoa: SeguirPessoa
    val seguindoRepository: SeguindoRepository
    val postsRepository: PostsRepository
}

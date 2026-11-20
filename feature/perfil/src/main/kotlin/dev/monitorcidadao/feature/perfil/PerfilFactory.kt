package dev.monitorcidadao.feature.perfil

import androidx.compose.runtime.Composable
import dev.monitorcidadao.core.routing.Router
import dev.monitorcidadao.domain.PessoaId
import dev.monitorcidadao.domain.PostId

/**
 * O que a feature Perfil precisa que o app monte (arquitetura §4). Só o `AppFactory` implementa:
 * ele conhece os repositórios e use cases concretos. Preview e teste usam uma factory falsa.
 */
interface PerfilFactory {
    @Composable
    fun makePerfil(id: PessoaId, router: Router)

    /** Placeholder do detalhe do post (destino de "Ver explicação"). */
    @Composable
    fun makePost(id: PostId, router: Router)
}

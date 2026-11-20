package dev.monitorcidadao.feature.perfil

import dev.monitorcidadao.core.designsystem.component.post.PostCardModel
import dev.monitorcidadao.domain.Post
import dev.monitorcidadao.domain.PostBloco
import dev.monitorcidadao.domain.PostTipo
import dev.monitorcidadao.core.designsystem.component.post.PostBloco as CardBloco
import dev.monitorcidadao.core.designsystem.component.post.PostTipo as CardTipo

/** Domain → modelo de apresentação do card (o DesignSystem não conhece o Domain). */
internal fun Post.paraCardModel(): PostCardModel = PostCardModel(
    id = id.valor,
    tipo = when (tipo) {
        PostTipo.Apresentou -> CardTipo.Apresentou
        PostTipo.Votou -> CardTipo.Votou
        PostTipo.Gastou -> CardTipo.Gastou
        PostTipo.Discursou -> CardTipo.Discursou
        is PostTipo.Desconhecido -> CardTipo.Desconhecido
    },
    publicadoEm = publicadoEm.toLocalDate(),
    titulo = titulo,
    corpo = corpo,
    blocos = blocos.map { bloco ->
        when (bloco) {
            is PostBloco.Placar -> CardBloco.Placar(bloco.sim, bloco.nao, bloco.abstencao)
            is PostBloco.ValorDestaque -> CardBloco.ValorDestaque(bloco.valor.centavos, bloco.rotulo)
            is PostBloco.Fonte -> CardBloco.Fonte(bloco.texto, bloco.url)
        }
    },
    temExplicacao = temExplicacao,
)

package dev.monitorcidadao.core.designsystem.component.post

import androidx.compose.runtime.Immutable
import java.time.LocalDate

/**
 * Modelo de apresentação do post (o DesignSystem não conhece o Domain — arquitetura §2).
 * A feature converte `Post` do domínio para cá. Tipos e blocos desconhecidos já chegam marcados,
 * e o renderer decide: tipo desconhecido → card genérico; bloco desconhecido → ignorado.
 */
@Immutable
data class PostCardModel(
    val id: String,
    val tipo: PostTipo,
    val publicadoEm: LocalDate,
    val titulo: String,
    val corpo: String,
    val blocos: List<PostBloco> = emptyList(),
    val temExplicacao: Boolean = false,
)

enum class PostTipo { Apresentou, Votou, Gastou, Discursou, Desconhecido }

sealed interface PostBloco {
    data class Placar(val sim: Int, val nao: Int, val abstencao: Int) : PostBloco
    data class ValorDestaque(val centavos: Long, val rotulo: String) : PostBloco
    data class Fonte(val texto: String, val url: String?) : PostBloco

    /** Bloco que esta versão do app não sabe desenhar. Nunca é renderizado. */
    data class Desconhecido(val tipo: String) : PostBloco
}

/** O que o card desenha, na ordem de leitura. Função pura: o teste confere sem subir UI. */
sealed interface PostCardParte {
    /** Chip do tipo + data curta (fora da árvore de a11y; tipo e data entram no título falado). */
    data object Cabecalho : PostCardParte

    /** Só a data (card genérico, sem chip de tipo). */
    data object Data : PostCardParte
    data object Titulo : PostCardParte
    data object Corpo : PostCardParte
    data class Bloco(val bloco: PostBloco) : PostCardParte
    data object VerExplicacao : PostCardParte
}

object PostCardLayout {
    /** O card genérico só desenha fonte entre os blocos (título + corpo + fonte, spec da tela 01). */
    fun isGeneric(model: PostCardModel): Boolean = model.tipo == PostTipo.Desconhecido

    fun partes(model: PostCardModel): List<PostCardParte> = buildList {
        val generic = isGeneric(model)
        add(if (generic) PostCardParte.Data else PostCardParte.Cabecalho)
        add(PostCardParte.Titulo)
        if (model.corpo.isNotBlank()) add(PostCardParte.Corpo)
        model.blocos
            .filterNot { it is PostBloco.Desconhecido }
            .filter { !generic || it is PostBloco.Fonte }
            .forEach { add(PostCardParte.Bloco(it)) }
        if (model.temExplicacao && !generic) add(PostCardParte.VerExplicacao)
    }
}

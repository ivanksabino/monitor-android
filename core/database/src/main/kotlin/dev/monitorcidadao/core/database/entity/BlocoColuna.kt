package dev.monitorcidadao.core.database.entity

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Forma gravada de um bloco de post (coluna JSON `post.blocos`, via TypeConverter).
 * Só blocos conhecidos chegam ao banco: o desconhecido já foi descartado no mapeamento.
 */
@Serializable
sealed interface BlocoColuna {
    @Serializable
    @SerialName("placar")
    data class Placar(val sim: Int, val nao: Int, val abstencao: Int) : BlocoColuna

    @Serializable
    @SerialName("valor_destaque")
    data class ValorDestaque(val centavos: Long, val rotulo: String) : BlocoColuna

    @Serializable
    @SerialName("fonte")
    data class Fonte(val texto: String, val url: String? = null) : BlocoColuna
}

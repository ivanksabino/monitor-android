package dev.monitorcidadao.domain

import kotlinx.serialization.Serializable

/** Identificadores opacos do contrato da API (strings). Tipados para não trocar um pelo outro. */
@Serializable
@JvmInline
value class PessoaId(val valor: String) {
    override fun toString(): String = valor
}

@Serializable
@JvmInline
value class PostId(val valor: String) {
    override fun toString(): String = valor
}

@Serializable
@JvmInline
value class TemaId(val valor: String) {
    override fun toString(): String = valor
}

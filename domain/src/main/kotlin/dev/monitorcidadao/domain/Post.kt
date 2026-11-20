package dev.monitorcidadao.domain

import java.time.OffsetDateTime

/** Item da linha do tempo de um parlamentar. Polimórfico pelo [tipo] e pelos [blocos]. */
data class Post(
    val id: PostId,
    val tipo: PostTipo,
    val pessoaId: PessoaId,
    val publicadoEm: OffsetDateTime,
    val titulo: String,
    val corpo: String,
    val blocos: List<PostBloco>,
    val temExplicacao: Boolean,
)

/** Tipo do post. Tipo que o app não conhece vira [Desconhecido] (card genérico, o app não quebra). */
sealed interface PostTipo {
    data object Apresentou : PostTipo
    data object Votou : PostTipo
    data object Gastou : PostTipo
    data object Discursou : PostTipo

    /** Guarda o texto original para o cache e para diagnóstico. */
    data class Desconhecido(val original: String) : PostTipo

    /** Texto do contrato (`apresentou`, ...), usado para gravar no cache. */
    val valorApi: String
        get() = when (this) {
            Apresentou -> "apresentou"
            Votou -> "votou"
            Gastou -> "gastou"
            Discursou -> "discursou"
            is Desconhecido -> original
        }

    companion object {
        fun from(valorApi: String): PostTipo = when (valorApi) {
            "apresentou" -> Apresentou
            "votou" -> Votou
            "gastou" -> Gastou
            "discursou" -> Discursou
            else -> Desconhecido(valorApi)
        }
    }
}

/** Bloco de conteúdo do post. Bloco desconhecido é descartado no mapeamento (nunca chega aqui). */
sealed interface PostBloco {
    data class Placar(val sim: Int, val nao: Int, val abstencao: Int) : PostBloco
    data class ValorDestaque(val valor: Dinheiro, val rotulo: String) : PostBloco
    data class Fonte(val texto: String, val url: String?) : PostBloco
}

/** Uma página do contrato paginado por cursor. `proximoCursor == null` = fim. */
data class Pagina<Item>(val itens: List<Item>, val proximoCursor: String?)

typealias PaginaPosts = Pagina<Post>

package dev.monitorcidadao.core.analytics

/** Telas com nome estável para analytics (não muda com tradução). */
enum class Tela(val key: String) {
    Inicio("inicio"),
    Explorar("explorar"),
    Entenda("entenda"),
    Participe("participe"),
    Voce("voce"),
    Perfil("perfil"),
    Post("post"),
}

/**
 * Catálogo tipado de eventos. Novo evento = novo caso aqui; nome e parâmetros ficam num só lugar.
 * Ids são strings opacas (Analytics não depende do Domain).
 */
sealed interface AnalyticsEvent {
    val name: String
    val params: Map<String, String> get() = emptyMap()

    data class TelaVista(val tela: Tela) : AnalyticsEvent {
        override val name = "tela_vista"
        override val params = mapOf("tela" to tela.key)
    }

    data class PerfilAberto(val pessoaId: String, val origem: Tela) : AnalyticsEvent {
        override val name = "perfil_aberto"
        override val params = mapOf("pessoa_id" to pessoaId, "origem" to origem.key)
    }

    data class SeguiuPessoa(val pessoaId: String) : AnalyticsEvent {
        override val name = "seguiu_pessoa"
        override val params = mapOf("pessoa_id" to pessoaId)
    }

    data class DeixouDeSeguir(val pessoaId: String) : AnalyticsEvent {
        override val name = "deixou_de_seguir"
        override val params = mapOf("pessoa_id" to pessoaId)
    }

    data class SegmentoTrocado(val tela: Tela, val segmento: String) : AnalyticsEvent {
        override val name = "segmento_trocado"
        override val params = mapOf("tela" to tela.key, "segmento" to segmento)
    }

    data class ExplicacaoAberta(val postId: String) : AnalyticsEvent {
        override val name = "explicacao_aberta"
        override val params = mapOf("post_id" to postId)
    }

    data class PostCompartilhado(val postId: String) : AnalyticsEvent {
        override val name = "post_compartilhado"
        override val params = mapOf("post_id" to postId)
    }

    data class ErroExibido(val tela: Tela, val codigo: String) : AnalyticsEvent {
        override val name = "erro_exibido"
        override val params = mapOf("tela" to tela.key, "codigo" to codigo)
    }
}

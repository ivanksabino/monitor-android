package dev.monitorcidadao.core.routing

import androidx.navigation3.runtime.NavKey
import dev.monitorcidadao.domain.PessoaId
import dev.monitorcidadao.domain.PostId
import dev.monitorcidadao.domain.TemaId
import kotlinx.serialization.Serializable

/** Destinos empilháveis. Navegação entre features é sempre por `Route` (feature não importa feature). */
@Serializable
sealed interface Route : NavKey {
    // Raízes das abas
    @Serializable data object Inicio : Route
    @Serializable data object Explorar : Route
    @Serializable data object Entenda : Route
    @Serializable data object Participe : Route
    @Serializable data object Voce : Route

    // Destinos
    @Serializable data class Perfil(val id: PessoaId) : Route
    @Serializable data class Post(val id: PostId) : Route
    @Serializable data class Tema(val id: TemaId) : Route
    @Serializable data object Veiculos : Route
}

/** Conteúdo apresentado por cima da pilha (sheet/diálogo). Devolve o foco ao gatilho ao fechar (a11y §4). */
sealed interface Sheet {
    data class Compartilhar(val post: PostId) : Sheet
    data class CompartilharPerfil(val pessoa: PessoaId) : Sheet
    data class Alertas(val pessoa: PessoaId) : Sheet
    data object Filtro : Sheet
}

/** Abas da barra inferior, cada uma com seu `Router`. */
enum class Tab(val root: Route, val testTag: String) {
    Inicio(Route.Inicio, "tab_inicio"),
    Explorar(Route.Explorar, "tab_explorar"),
    Entenda(Route.Entenda, "tab_entenda"),
    Participe(Route.Participe, "tab_participe"),
    Voce(Route.Voce, "tab_voce"),
}

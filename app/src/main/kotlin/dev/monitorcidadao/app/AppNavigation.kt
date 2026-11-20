package dev.monitorcidadao.app

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.monitorcidadao.core.routing.DeepLink
import dev.monitorcidadao.core.routing.DeepLinkParser
import dev.monitorcidadao.core.routing.Router
import dev.monitorcidadao.core.routing.Tab

/**
 * Estado de navegação do app: um [Router] (pilha própria) por aba e a aba selecionada.
 * Vive num ViewModel da Activity, então sobrevive à rotação.
 */
@Stable
class AppNavigation(inicial: Tab = Tab.Inicio) {
    private val routers: Map<Tab, Router> = Tab.entries.associateWith { Router(it.root) }

    var selecionada: Tab by mutableStateOf(inicial)
        private set

    val atual: Router get() = router(selecionada)

    fun router(tab: Tab): Router = routers.getValue(tab)

    /** Tocar na aba já selecionada volta à raiz dela (padrão das barras de abas). */
    fun selecionar(tab: Tab) {
        if (tab == selecionada) atual.popToRoot() else selecionada = tab
    }

    /**
     * Deep link esperando a pilha da aba de destino entrar na composição (ver [abrir]).
     * Sobrevive à rotação junto com o resto do estado de navegação.
     */
    var linkPendente: DeepLink? by mutableStateOf(null)
        private set

    /**
     * Deep link: troca para a aba certa e fecha a sheet dela. A pilha só é substituída em
     * [aplicarLinkPendente], chamado pela pilha da aba depois que o `NavDisplay` dela está composto:
     * o Navigation 3 só limpa o ViewModel e o estado salvo de uma entrada que sai da pilha quando
     * vê a troca de pilha composto. Trocar aba e pilha no mesmo frame deixava a tela antiga viva.
     */
    fun abrir(link: DeepLink) {
        router(link.tab).dismissSheet()
        selecionada = link.tab
        linkPendente = link
    }

    /** Aplica o deep link pendente se ele for da [tab] (cuja pilha já está composta). */
    fun aplicarLinkPendente(tab: Tab) {
        val link = linkPendente?.takeIf { it.tab == tab } ?: return
        linkPendente = null
        router(tab).reset(link.stack)
    }

    /** `monitorcidadao://perfil/{id}` e afins. `false` (nada muda) para link inválido ou ausente. */
    fun abrir(uri: String?): Boolean {
        val link = uri?.let(DeepLinkParser::parse) ?: return false
        abrir(link)
        return true
    }
}

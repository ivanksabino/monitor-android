package dev.monitorcidadao.app

import dev.monitorcidadao.core.routing.Route
import dev.monitorcidadao.core.routing.Sheet
import dev.monitorcidadao.core.routing.Tab
import dev.monitorcidadao.domain.PessoaId
import dev.monitorcidadao.domain.PostId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AppNavigationTest {
    private val ana = PessoaId("cam-1001")

    @Test
    fun `cada aba comeca na propria raiz com router separado`() {
        val nav = AppNavigation()
        Tab.entries.forEach { assertEquals(listOf(it.root), nav.router(it).backStack.toList()) }
        nav.router(Tab.Inicio).push(Route.Perfil(ana))
        assertEquals(listOf(Route.Explorar), nav.router(Tab.Explorar).backStack.toList())
    }

    @Test
    fun `deep link de perfil abre na aba Inicio a partir de outra aba`() {
        val nav = AppNavigation()
        nav.selecionar(Tab.Explorar)
        nav.router(Tab.Inicio).push(Route.Post(PostId("antigo")))
        nav.router(Tab.Inicio).present(Sheet.Alertas(ana))

        assertTrue(nav.abrir("monitorcidadao://perfil/cam-1001"))

        assertEquals(Tab.Inicio, nav.selecionada)
        // A pilha só muda quando a aba de destino já está composta e aplica o link.
        assertEquals(listOf(Route.Inicio, Route.Post(PostId("antigo"))), nav.atual.backStack.toList())
        nav.aplicarLinkPendente(Tab.Explorar) // outra aba: não aplica
        assertEquals(listOf(Route.Inicio, Route.Post(PostId("antigo"))), nav.atual.backStack.toList())
        nav.aplicarLinkPendente(Tab.Inicio)
        assertNull(nav.linkPendente)
        assertEquals(listOf(Route.Inicio, Route.Perfil(ana)), nav.atual.backStack.toList())
        assertNull(nav.atual.sheet)
        assertEquals(listOf(Route.Explorar), nav.router(Tab.Explorar).backStack.toList())
    }

    @Test
    fun `link invalido ou ausente nao muda nada`() {
        val nav = AppNavigation()
        nav.selecionar(Tab.Voce)

        assertFalse(nav.abrir("https://exemplo.invalid/perfil/1"))
        assertFalse(nav.abrir("monitorcidadao://perfil"))
        assertFalse(nav.abrir(null))

        assertEquals(Tab.Voce, nav.selecionada)
        assertNull(nav.linkPendente)
    }

    @Test
    fun `tocar na aba ja selecionada volta para a raiz`() {
        val nav = AppNavigation()
        nav.atual.push(Route.Perfil(ana))
        nav.atual.push(Route.Post(PostId("p1")))

        nav.selecionar(Tab.Inicio)

        assertEquals(listOf(Route.Inicio), nav.atual.backStack.toList())
    }
}

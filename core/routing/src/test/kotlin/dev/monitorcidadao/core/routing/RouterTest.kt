package dev.monitorcidadao.core.routing

import dev.monitorcidadao.domain.PessoaId
import dev.monitorcidadao.domain.PostId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class RouterTest {
    private val perfil = Route.Perfil(PessoaId("cam-1001"))

    @Test
    fun `push e pop mantem a raiz`() {
        val router = Router(Route.Inicio)
        router.push(perfil)
        router.push(Route.Post(PostId("p1")))
        router.pop()
        assertEquals(listOf(Route.Inicio, perfil), router.backStack.toList())
        router.pop()
        router.pop()
        assertEquals(listOf(Route.Inicio), router.backStack.toList())
    }

    @Test
    fun `push duplicado da rota do topo nao cresce a pilha`() {
        val router = Router(Route.Inicio)
        router.push(perfil)
        router.push(perfil)
        assertEquals(listOf(Route.Inicio, perfil), router.backStack.toList())

        router.push(Route.Post(PostId("p1")))
        router.push(Route.Post(PostId("p1")))
        assertEquals(listOf(Route.Inicio, perfil, Route.Post(PostId("p1"))), router.backStack.toList())
    }

    @Test
    fun `popToRoot e reset de deep link`() {
        val router = Router(Route.Inicio)
        router.push(Route.Veiculos)
        router.reset(listOf(perfil))
        assertEquals(listOf(Route.Inicio, perfil), router.backStack.toList())
        router.popToRoot()
        assertEquals(Route.Inicio, router.current)
    }

    @Test
    fun `sheet apresenta e fecha`() {
        val router = Router(Route.Inicio)
        router.present(Sheet.Alertas(PessoaId("cam-1001")))
        assertEquals(Sheet.Alertas(PessoaId("cam-1001")), router.sheet)
        router.dismissSheet()
        assertNull(router.sheet)
    }
}

class DeepLinkParserTest {
    @Test
    fun `post abre na aba Inicio (igual ao iOS)`() {
        assertEquals(
            DeepLink(Tab.Inicio, listOf(Route.Post(dev.monitorcidadao.domain.PostId("post-1")))),
            DeepLinkParser.parse("monitorcidadao://post/post-1"),
        )
        assertNull(DeepLinkParser.parse("monitorcidadao://post"))
    }

    @Test
    fun `perfil abre na aba Inicio`() {
        assertEquals(
            DeepLink(Tab.Inicio, listOf(Route.Perfil(PessoaId("cam-1001")))),
            DeepLinkParser.parse("monitorcidadao://perfil/cam-1001"),
        )
    }

    @Test
    fun `links invalidos sao ignorados`() {
        assertNull(DeepLinkParser.parse("https://perfil/cam-1001"))
        assertNull(DeepLinkParser.parse("monitorcidadao://perfil"))
        assertNull(DeepLinkParser.parse("monitorcidadao://perfil/a/b"))
        assertNull(DeepLinkParser.parse("monitorcidadao://desconhecido/1"))
        assertNull(DeepLinkParser.parse("::: nao e uri"))
    }
}

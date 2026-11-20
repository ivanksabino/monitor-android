package dev.monitorcidadao.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.SemanticsPropertiesAndroid
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasParent
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import dev.monitorcidadao.core.analytics.AnalyticsEvent
import dev.monitorcidadao.core.analytics.Tela
import dev.monitorcidadao.core.designsystem.MonitorTheme
import dev.monitorcidadao.core.designsystem.a11y.A11yAnnouncer
import dev.monitorcidadao.core.routing.Route
import dev.monitorcidadao.core.routing.Router
import dev.monitorcidadao.core.routing.Tab
import dev.monitorcidadao.domain.PessoaId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** AppFactory (Route → tela) e casca (abas + deep link) com dependências falsas. */
@RunWith(RobolectricTestRunner::class)
class AppFactoryTest {
    val rule = createComposeRule()

    @get:Rule
    val regras: RuleChain = RuleChain.outerRule(RegistrarComponentActivity()).around(rule)

    private val deps = FakeAppDependencies()
    private val factory = AppFactory(deps)

    private fun setContent(leitorAtivo: Boolean = false, content: @Composable () -> Unit) = rule.setContent {
        MonitorTheme(darkTheme = false, reduceMotion = true, screenReaderEnabled = leitorAtivo, announcer = A11yAnnouncer.None) {
            content()
        }
    }

    private fun telasVistas(tela: Tela) = deps.analyticsSink.events.count { it == AnalyticsEvent.TelaVista(tela) }

    @Test
    fun `Route Perfil monta a tela de perfil com os dados da pessoa`() {
        val router = Router(Route.Inicio)
        setContent { factory.make(Route.Perfil(PessoaId("cam-1001")), router) }

        rule.onNodeWithTag("perfil_nome").assertTextEquals("Ana Ribeiro")
        rule.onNodeWithTag("perfil_seguir").assertIsDisplayed()
        assertTrue(deps.analyticsSink.events.contains(AnalyticsEvent.TelaVista(Tela.Perfil)))
    }

    @Test
    fun `deep link abre o Perfil na aba Inicio e voltar retorna a lista`() {
        val nav = AppNavigation()
        nav.selecionar(Tab.Participe)
        nav.abrir("monitorcidadao://perfil/cam-1001")
        setContent { MonitorApp(nav, factory) }

        rule.onNodeWithTag("tab_inicio").assertIsSelected()
        rule.onNodeWithTag("perfil_nome").assertTextEquals("Ana Ribeiro")

        rule.onNodeWithTag("perfil_voltar").performClick()
        rule.waitForIdle()

        assertEquals(listOf(Route.Inicio), nav.atual.backStack.toList())
        rule.onNodeWithTag("inicio_pessoa_cam-1001").assertIsDisplayed()
    }

    @Test
    fun `linha de quem voce segue abre o perfil e as abas trocam de pilha`() {
        val nav = AppNavigation()
        setContent { MonitorApp(nav, factory) }

        rule.onNodeWithTag("inicio_pessoa_cam-1001").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("perfil_nome").assertTextEquals("Ana Ribeiro")

        rule.onNodeWithTag("tab_explorar").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("explorar_titulo").assertTextEquals("Explorar")
        rule.onNodeWithTag("tab_explorar").assertIsSelected()

        rule.onNodeWithTag("tab_inicio").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("perfil_nome").assertIsDisplayed() // a pilha do Início foi preservada
    }

    @Test
    fun `deep link vindo de outra aba limpa o ViewModel do perfil que sai da pilha`() {
        val ana = PessoaId("cam-1001")
        val bia = PessoaId("cam-2002")
        val nav = AppNavigation()
        setContent { MonitorApp(nav, factory) }

        rule.onNodeWithTag("inicio_pessoa_cam-1001").performClick()
        rule.waitForIdle()
        assertEquals(1, deps.seguindoRepository.ativos(ana))

        rule.onNodeWithTag("tab_explorar").performClick()
        rule.waitForIdle()
        assertEquals("o perfil continua na pilha do Início", 1, deps.seguindoRepository.ativos(ana))

        rule.runOnIdle { nav.abrir("monitorcidadao://perfil/cam-2002") }
        rule.waitForIdle()

        rule.onNodeWithTag("tab_inicio").assertIsSelected()
        assertEquals(listOf(Route.Inicio, Route.Perfil(bia)), nav.atual.backStack.toList())
        assertEquals("PerfilViewModel(A) recebeu onCleared", 0, deps.seguindoRepository.ativos(ana))
        assertEquals(1, deps.seguindoRepository.ativos(bia))
        assertEquals(null, nav.linkPendente)
    }

    @Test
    fun `toque duplo na linha da pessoa empilha o perfil uma vez`() {
        val nav = AppNavigation()
        setContent { MonitorApp(nav, factory) }

        rule.onNodeWithTag("inicio_pessoa_cam-1001").performClick()
        rule.runOnIdle { nav.atual.push(Route.Perfil(PessoaId("cam-1001"))) }
        rule.waitForIdle()

        assertEquals(listOf(Route.Inicio, Route.Perfil(PessoaId("cam-1001"))), nav.atual.backStack.toList())
    }

    @Test
    fun `TelaVista conta uma vez por entrada mesmo com rotacao`() {
        val router = Router(Route.Inicio)
        val restauracao = StateRestorationTester(rule)
        restauracao.setContent {
            MonitorTheme(darkTheme = false, reduceMotion = true, screenReaderEnabled = false, announcer = A11yAnnouncer.None) {
                factory.make(Route.Perfil(PessoaId("cam-1001")), router)
            }
        }
        rule.waitForIdle()
        assertEquals(1, telasVistas(Tela.Perfil))

        restauracao.emulateSavedInstanceStateRestore()
        rule.waitForIdle()

        assertEquals(1, telasVistas(Tela.Perfil))
    }

    @Test
    fun `voltar para a aba nao conta outra TelaVista`() {
        val nav = AppNavigation()
        setContent { MonitorApp(nav, factory) }
        rule.waitForIdle()
        assertEquals(1, telasVistas(Tela.Inicio))

        rule.onNodeWithTag("tab_explorar").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("tab_inicio").performClick()
        rule.waitForIdle()

        assertEquals(1, telasVistas(Tela.Inicio))
        assertEquals(1, telasVistas(Tela.Explorar))
    }

    @Test
    fun `Alertas abre a sheet e Fechar devolve ao perfil`() {
        val nav = AppNavigation()
        nav.abrir("monitorcidadao://perfil/cam-1001")
        setContent { MonitorApp(nav, factory) }

        rule.onNodeWithTag("perfil_alertas").performClick()
        rule.waitForIdle()
        // Mesmos identificadores e textos do iOS (BUG-004).
        rule.onNodeWithTag(AppTags.ALERTAS_TITULO).assertTextEquals("Alertas — em breve").assert(isHeading())
        rule.onNodeWithText("Logo você vai poder receber um aviso quando houver novidade sobre esta pessoa.").assertIsDisplayed()
        rule.onNodeWithTag(AppTags.ALERTAS_FECHAR).assertTextEquals("Fechar").assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
        rule.onNodeWithTag(AppTags.SHEET_TITULO).assertDoesNotExist()
        // A sheet é outra janela: a raiz do conteúdo dela precisa ligar testTagsAsResourceId
        // (a raiz do app não alcança o dialog), senão o Trail não vê os ids (BUG-004, r2).
        val comResourceId = hasAnyAncestor(SemanticsMatcher.expectValue(SemanticsPropertiesAndroid.TestTagsAsResourceId, true))
        rule.onNodeWithTag(AppTags.ALERTAS_TITULO).assert(comResourceId)
        rule.onNodeWithTag(AppTags.ALERTAS_FECHAR).assert(comResourceId)

        rule.onNodeWithTag(AppTags.ALERTAS_FECHAR).performClick()
        rule.waitForIdle()
        assertEquals(null, nav.atual.sheet)
    }

    private fun abrirEFecharSheet(gatilho: String, leitorAtivo: Boolean): AppNavigation {
        val nav = AppNavigation()
        nav.abrir("monitorcidadao://perfil/cam-1001")
        setContent(leitorAtivo = leitorAtivo) { MonitorApp(nav, factory) }

        rule.onNodeWithTag(gatilho).performClick()
        rule.waitForIdle()
        rule.onNodeWithTag(if (gatilho == "perfil_alertas") AppTags.ALERTAS_FECHAR else AppTags.SHEET_FECHAR).performClick()
        rule.waitForIdle()
        assertEquals(null, nav.atual.sheet)
        return nav
    }

    private fun focado(tag: String) =
        rule.onNodeWithTag(tag).fetchSemanticsNode().config.getOrNull(SemanticsProperties.Focused) == true

    /** A animação da sheet e o atraso de 350 ms do foco correm em tempo real no teste. */
    private fun esperarFoco(tag: String) = rule.waitUntil(timeoutMillis = 3_000) { focado(tag) }

    @Test
    fun `com leitor ativo fechar Alertas devolve o foco ao botao Alertas`() {
        abrirEFecharSheet("perfil_alertas", leitorAtivo = true)
        esperarFoco("perfil_alertas")
        rule.onNodeWithTag("perfil_alertas").assertIsFocused()
    }

    @Test
    fun `com leitor ativo fechar Compartilhar devolve o foco ao botao Compartilhar`() {
        abrirEFecharSheet("perfil_compartilhar", leitorAtivo = true)
        esperarFoco("perfil_compartilhar")
        rule.onNodeWithTag("perfil_compartilhar").assertIsFocused()
    }

    @Test
    fun `sem leitor fechar a sheet nao move o foco`() {
        abrirEFecharSheet("perfil_alertas", leitorAtivo = false)
        Thread.sleep(700) // mais que o atraso do foco
        rule.waitForIdle()
        rule.onNodeWithTag("perfil_alertas").assertIsNotFocused()
    }
}

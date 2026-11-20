package dev.monitorcidadao.app

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasParent
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import dev.monitorcidadao.core.designsystem.MonitorTheme
import dev.monitorcidadao.core.designsystem.a11y.A11yAnnouncer
import dev.monitorcidadao.core.routing.Tab
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Barra de abas própria (a11y §5 e §13). Gráficos nativos do Robolectric para medir texto de
 * verdade; largura de celular estreito (360 dp), o pior caso para os 5 rótulos.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h740dp-xxhdpi")
class BarraDeAbasTest {
    val rule = createComposeRule()

    @get:Rule
    val regras: RuleChain = RuleChain.outerRule(RegistrarComponentActivity()).around(rule)

    private val rotulos = mapOf(
        Tab.Inicio to "Início",
        Tab.Explorar to "Explorar",
        Tab.Entenda to "Entenda",
        Tab.Participe to "Participe",
        Tab.Voce to "Você",
    )

    private val tocadas = mutableListOf<Tab>()

    private fun render(fontScale: Float, inicial: Tab = Tab.Inicio) {
        var selecionada by mutableStateOf(inicial)
        rule.setContent {
            val densidade = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(densidade.density, fontScale)) {
                MonitorTheme(darkTheme = false, reduceMotion = true, screenReaderEnabled = false, announcer = A11yAnnouncer.None) {
                    BarraDeAbas(selecionada, onSelecionar = { tocadas += it; selecionada = it })
                }
            }
        }
    }

    private fun hasRole(role: Role) = SemanticsMatcher.expectValue(SemanticsProperties.Role, role)

    private fun Rect.contem(outro: Rect): Boolean =
        outro.left >= left - 0.5f && outro.top >= top - 0.5f && outro.right <= right + 0.5f && outro.bottom <= bottom + 0.5f

    private fun conferirRotulosSemCorte() {
        rotulos.forEach { (tab, rotulo) ->
            val item = rule.onNodeWithTag(tab.testTag).assert(hasRole(Role.Tab)).fetchSemanticsNode()
            val texto = rule.onNode(hasText(rotulo) and hasParent(hasTestTag(tab.testTag)), useUnmergedTree = true)
            texto.assertIsDisplayed()
            val layouts = mutableListOf<TextLayoutResult>()
            texto.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            val layout = layouts.single()
            assertFalse("$rotulo estoura a altura", layout.didOverflowHeight)
            // didOverflowWidth não serve com TextAlign.Center (o parágrafo ocupa a largura máxima e a
            // caixa fica com a largura do texto): confere que cada linha cabe na largura disponível.
            val larguraDisponivel = layout.layoutInput.constraints.maxWidth
            (0 until layout.lineCount).forEach { linha ->
                val largura = layout.getLineRight(linha) - layout.getLineLeft(linha)
                assertTrue("$rotulo: linha $linha ($largura px) passa de $larguraDisponivel px", largura <= larguraDisponivel + 0.5f)
            }
            val caixa = texto.fetchSemanticsNode().boundsInRoot
            assertTrue("$rotulo ($caixa) passa do item da aba (${item.boundsInRoot})", item.boundsInRoot.contem(caixa))
        }
    }

    @Test
    fun `com fontScale 2 os 5 rotulos aparecem inteiros e a barra cresce`() {
        render(fontScale = 2f)
        conferirRotulosSemCorte()
        val altura = rule.onNodeWithTag(Tab.Participe.testTag).fetchSemanticsNode().boundsInRoot.height
        val densidade = rule.density.density
        assertTrue("a barra cresce além dos 80 dp ($altura px)", altura > 80 * densidade)
    }

    @Test
    fun `com fonte padrao os rotulos cabem e a barra tem pelo menos 80 dp`() {
        render(fontScale = 1f)
        conferirRotulosSemCorte()
        val altura = rule.onNodeWithTag(Tab.Inicio.testTag).fetchSemanticsNode().boundsInRoot.height
        assertTrue(altura >= 80 * rule.density.density - 0.5f)
    }

    @Test
    fun `contrato de abas com selecionado e toque na aba atual`() {
        render(fontScale = 1f, inicial = Tab.Explorar)

        rule.onNodeWithTag(Tab.Explorar.testTag).assertIsSelected()
        Tab.entries.filter { it != Tab.Explorar }.forEach { rule.onNodeWithTag(it.testTag).assertIsNotSelected() }

        rule.onNodeWithTag(Tab.Explorar.testTag).performClick() // a casca leva à raiz da aba
        rule.onNodeWithTag(Tab.Participe.testTag).performClick()
        rule.onNodeWithTag(Tab.Participe.testTag).assertIsSelected()
        assertEquals(listOf(Tab.Explorar, Tab.Participe), tocadas)
    }
}

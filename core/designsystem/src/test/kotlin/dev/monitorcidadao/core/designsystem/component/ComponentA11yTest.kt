package dev.monitorcidadao.core.designsystem.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import dev.monitorcidadao.core.designsystem.MonitorTheme
import dev.monitorcidadao.core.designsystem.a11y.A11yAnnouncer
import dev.monitorcidadao.core.designsystem.a11y.assertNoUnico
import dev.monitorcidadao.core.designsystem.a11y.noDeAcessibilidade
import dev.monitorcidadao.core.designsystem.icon.MonitorIcons
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Semântica de acessibilidade dos componentes (a11y-nativo.md), conferida na árvore do Compose. */
@RunWith(RobolectricTestRunner::class)
class ComponentA11yTest {
    @get:Rule
    val rule = createComposeRule()

    private fun setContent(content: @Composable () -> Unit) = rule.setContent {
        MonitorTheme(darkTheme = false, reduceMotion = true, screenReaderEnabled = false, announcer = A11yAnnouncer.None) {
            content()
        }
    }

    private fun hasRole(role: Role) = SemanticsMatcher.expectValue(SemanticsProperties.Role, role)
    private fun hasState(state: String) = SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, state)

    @Test
    fun `StatCard e um elemento com label humano`() {
        setContent { StatCard(value = "92%", caption = "de presença", accessibilityLabel = "92% de presença") }
        rule.onNodeWithContentDescription("92% de presença").assertExists()
        rule.onNodeWithText("92%").assertDoesNotExist()
        rule.onNodeWithText("de presença").assertDoesNotExist()
    }

    @Test
    fun `StatusChipsGroup vira um elemento com o texto completo`() {
        setContent {
            StatusChipsGroup(
                listOf(ChipModel("Em exercício", ChipTone.Accent), ChipModel("Mandato 2027–2031"), ChipModel("1º mandato")),
            )
        }
        rule.onNodeWithContentDescription("Em exercício, Mandato 2027–2031, 1º mandato").assertExists()
        rule.onNodeWithText("1º mandato").assertDoesNotExist()
    }

    @Test
    fun `ToggleButton mantem o label fixo e muda so o estado`() {
        setContent {
            var seguindo by remember { mutableStateOf(false) }
            ToggleButton(
                checked = seguindo, onCheckedChange = { seguindo = it },
                textOff = "Seguir", textOn = "Seguindo", accessibilityLabel = "Seguir Ana Ribeiro",
                stateOn = "seguindo", stateOff = "não seguindo",
            )
        }
        val botao = rule.onNodeWithContentDescription("Seguir Ana Ribeiro")
        botao.assert(hasState("não seguindo"))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.ToggleableState, ToggleableState.Off))
            .assert(hasRole(Role.Button))
            .assertHeightIsAtLeast(48.dp)

        botao.performClick()

        rule.onNodeWithContentDescription("Seguir Ana Ribeiro")
            .assert(hasState("seguindo"))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.ToggleableState, ToggleableState.On))
        // O texto visual ("Seguindo") não vira uma segunda fala.
        rule.onNodeWithText("Seguindo").assertDoesNotExist()
    }

    @Test
    fun `CircleIconButton tem nome, papel de botao e alvo de 48dp`() {
        setContent { CircleIconButton(MonitorIcons.Voltar, contentDescription = "Voltar", onClick = {}) }
        rule.onNodeWithContentDescription("Voltar")
            .assert(hasRole(Role.Button))
            .assertHasClickAction()
            .assertWidthIsAtLeast(48.dp)
            .assertHeightIsAtLeast(48.dp)
    }

    @Test
    fun `SegmentedTabs cumpre o contrato de abas e a troca e idempotente`() {
        var trocas = 0
        setContent {
            var selecionada by remember { mutableIntStateOf(0) }
            SegmentedTabs(
                options = listOf(TabOption("Resumo", "seg_resumo"), TabOption("Em números", "seg_numeros")),
                selectedIndex = selecionada,
                onSelect = { trocas++; selecionada = it },
            )
        }
        rule.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.SelectableGroup)).assertExists()
        rule.onNodeWithTag("seg_resumo").assert(hasRole(Role.Tab)).assertIsSelected().assertHeightIsAtLeast(48.dp)
        rule.onNodeWithTag("seg_numeros").assert(hasRole(Role.Tab)).assertIsNotSelected()

        rule.onNodeWithTag("seg_resumo").performClick()
        assertEquals("tocar na aba já selecionada não troca", 0, trocas)

        rule.onNodeWithTag("seg_numeros").performClick()
        assertEquals(1, trocas)
        rule.onNodeWithTag("seg_numeros").assertIsSelected()
        rule.onNodeWithTag("seg_resumo").assertIsNotSelected()
    }

    @Test
    fun `SectionTabs roláveis tambem sao abas`() {
        setContent {
            SectionTabs(
                options = listOf("Posts", "Sobre").map { TabOption(it, "perfil_secao_$it") },
                selectedIndex = 0,
                onSelect = {},
            )
        }
        rule.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.SelectableGroup)).assertExists()
        rule.onNodeWithTag("perfil_secao_Posts").assert(hasRole(Role.Tab)).assertIsSelected().assertHeightIsAtLeast(48.dp)
        rule.onNodeWithTag("perfil_secao_Sobre").assert(hasRole(Role.Tab)).assertIsNotSelected()
    }

    @Test
    fun `SectionHeader e cabecalho e overline fala o texto original`() {
        setContent {
            androidx.compose.foundation.layout.Column {
                SectionHeader("Ana Ribeiro", HeadingLevel.H1)
                SectionHeader("Presença · últimas 20 sessões", HeadingLevel.Overline)
            }
        }
        rule.onNodeWithText("Ana Ribeiro").assert(isHeading())
        rule.onNodeWithContentDescription("Presença · últimas 20 sessões").assert(isHeading())
        rule.onNodeWithText("PRESENÇA · ÚLTIMAS 20 SESSÕES").assertDoesNotExist()
    }

    @Test
    fun `PresenceGrid e uma imagem com resumo em texto`() {
        setContent { PresenceGrid(List(20) { it != 5 && it != 14 }) }
        rule.onNodeWithContentDescription("Presente em 18 das últimas 20 sessões; faltou na 6ª e na 15ª")
            .assert(hasRole(Role.Image))
    }

    @Test
    fun `DataRow fala o label humano, nao a chave tecnica`() {
        setContent { DataRow("cota_2027", "R$ 112.340,18", "Cota parlamentar em 2027: 112 mil reais") }
        rule.onNodeWithContentDescription("Cota parlamentar em 2027: 112 mil reais").assertExists()
        rule.onNodeWithText("cota_2027").assertDoesNotExist()
    }

    @Test
    fun `ListFooter carregando tem label e nao e live region`() {
        setContent { ListFooter(ListFooterState.Carregando, onRetry = {}, loadingLabel = "Carregando mais posts") }
        rule.onNodeWithContentDescription("Carregando mais posts")
            .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.LiveRegion))
    }

    @Test
    fun `ListFooter erro tem Tentar de novo focavel e o fim tem testTag`() {
        var tentativas = 0
        var estado by mutableStateOf(ListFooterState.Erro)
        setContent { ListFooter(estado, onRetry = { tentativas++ }) }
        rule.onNodeWithText("Não foi possível carregar mais.").assertExists()
        rule.onNodeWithTag(ListFooterTags.TENTAR).assert(hasRole(Role.Button)).assertHeightIsAtLeast(48.dp).performClick()
        assertEquals(1, tentativas)

        estado = ListFooterState.Fim
        rule.onNodeWithTag(ListFooterTags.FIM).assertExists()
        rule.onAllNodesWithText("Tentar de novo").assertCountEquals(0)
    }

    @Test
    fun `SkeletonView e um elemento com label ou sai da arvore`() {
        var label by mutableStateOf<String?>("Carregando perfil")
        setContent { SkeletonView(label) { SkeletonBlock() } }
        rule.onNode(hasContentDescription("Carregando perfil")).assertExists()

        label = null
        rule.onNodeWithTag(SKELETON_TAG)
            .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.ContentDescription))
    }

    @Test
    fun `SecondaryButton com icone fala so o texto`() {
        setContent { SecondaryButton("Alertas", onClick = {}, icon = MonitorIcons.Sino) }
        rule.onNodeWithText("Alertas").assert(hasRole(Role.Button)).assertHeightIsAtLeast(48.dp)
            .assertContentDescriptionEqualsNone()
    }

    // --- BUG-006: testTag, nome, papel e estado no MESMO nó da árvore de acessibilidade ---

    @Test
    fun `ToggleButton expoe id, nome, papel e estado no mesmo no`() {
        setContent {
            ToggleButton(
                checked = true, onCheckedChange = {}, textOff = "Seguir", textOn = "Seguindo",
                accessibilityLabel = "Seguir Ana Ribeiro", stateOn = "seguindo", stateOff = "não seguindo",
                modifier = Modifier.testTag("perfil_seguir"),
            )
        }
        val no = rule.noDeAcessibilidade("perfil_seguir")
        no.assertNoUnico(nome = "Seguir Ana Ribeiro", classe = "android.widget.Button")
        assertEquals("seguindo", no.stateDescription?.toString())
        assertTrue(no.isCheckable && no.isClickable && no.isFocusable)
    }

    @Test
    fun `SecondaryButton com icone expoe id e nome no mesmo no`() {
        setContent { SecondaryButton("Alertas", onClick = {}, icon = MonitorIcons.Sino, modifier = Modifier.testTag("perfil_alertas")) }
        val no = rule.noDeAcessibilidade("perfil_alertas")
        no.assertNoUnico(nome = "Alertas", classe = "android.widget.Button")
        assertTrue(no.isClickable && no.isFocusable)
        rule.onNodeWithTag("perfil_alertas").assertTextEquals("Alertas")
    }

    @Test
    fun `PrimaryButton com nome explicito nao repete o texto visivel`() {
        setContent { PrimaryButton("Tentar de novo", onClick = {}, accessibilityLabel = "Tentar de novo carregar o perfil", modifier = Modifier.testTag("b")) }
        rule.noDeAcessibilidade("b").assertNoUnico(nome = "Tentar de novo carregar o perfil", classe = "android.widget.Button")
        rule.onAllNodesWithText("Tentar de novo").assertCountEquals(0)
    }

    @Test
    fun `abas expoem id, nome, papel e selecionado no mesmo no`() {
        setContent {
            androidx.compose.foundation.layout.Column {
                SegmentedTabs(listOf(TabOption("Resumo", "seg_resumo"), TabOption("Em números", "seg_numeros")), 0, {})
                SectionTabs(listOf(TabOption("Posts", "secao_posts"), TabOption("Sobre", "secao_sobre")), 0, {})
            }
        }
        listOf("seg_resumo" to "Resumo", "seg_numeros" to "Em números", "secao_posts" to "Posts", "secao_sobre" to "Sobre")
            .forEach { (tag, nome) ->
                val no = rule.noDeAcessibilidade(tag)
                assertEquals("nome de $tag", nome, no.text?.toString())
                assertEquals("$tag sem filhos", 0, no.childCount)
                assertTrue("$tag focável", no.isFocusable)
            }
        assertTrue(rule.noDeAcessibilidade("seg_resumo").isSelected)
        assertTrue(rule.noDeAcessibilidade("secao_posts").isSelected)
        rule.onNodeWithTag("seg_numeros").assertTextEquals("Em números")
    }

    private fun androidx.compose.ui.test.SemanticsNodeInteraction.assertContentDescriptionEqualsNone() =
        assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.ContentDescription))
}

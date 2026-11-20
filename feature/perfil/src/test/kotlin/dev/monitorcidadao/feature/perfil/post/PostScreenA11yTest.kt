package dev.monitorcidadao.feature.perfil.post

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import dev.monitorcidadao.core.designsystem.MonitorTheme
import dev.monitorcidadao.core.designsystem.a11y.A11yAnnouncer
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Tela Post (BUG-005): `post_titulo` e o h1 são o título do post, como no iOS; "Post" é só a barra. */
@RunWith(RobolectricTestRunner::class)
class PostScreenA11yTest {
    @get:Rule
    val rule = createComposeRule()

    private val acoes = mutableListOf<PostViewModel.Action>()

    private fun setContent(state: () -> PostViewModel.State) = rule.setContent {
        MonitorTheme(darkTheme = false, reduceMotion = true, screenReaderEnabled = false, announcer = A11yAnnouncer.None) {
            PostScreen(state(), send = { acoes += it })
        }
    }

    private fun titulos() = rule.onAllNodes(isHeading()).fetchSemanticsNodes()
        .map { it.config.getOrNull(SemanticsProperties.Text)?.firstOrNull()?.text }

    @Test
    fun `post_titulo e o unico h1 e trazem o titulo do post`() {
        setContent { PostViewModel.State(titulo = "Votou sim no PL do transporte escolar rural", carregando = false) }

        rule.onNodeWithTag(PostTags.TITULO)
            .assertTextEquals("Votou sim no PL do transporte escolar rural")
            .assert(isHeading())
        assertEquals(listOf("Votou sim no PL do transporte escolar rural"), titulos())
        rule.onNode(SemanticsMatcher.expectValue(SemanticsProperties.PaneTitle, "Post")).assertExists()
    }

    @Test
    fun `enquanto carrega o h1 mostra Post e troca pelo titulo quando chega`() {
        var state by mutableStateOf(PostViewModel.State())
        setContent { state }
        rule.onNodeWithTag(PostTags.TITULO).assertTextEquals("Post")

        state = PostViewModel.State(titulo = "Gastou R$ 12.900 com passagens", carregando = false)
        rule.onNodeWithTag(PostTags.TITULO).assertTextEquals("Gastou R$ 12.900 com passagens")
    }

    @Test
    fun `Voltar continua com testTag e nome`() {
        setContent { PostViewModel.State(titulo = "Título", carregando = false) }
        rule.onNodeWithTag(PostTags.VOLTAR).assert(hasContentDescription("Voltar")).performClick()
        assertEquals(listOf<PostViewModel.Action>(PostViewModel.Action.TocouVoltar), acoes)
    }
}

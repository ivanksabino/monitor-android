package dev.monitorcidadao.core.designsystem.component.post

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.monitorcidadao.core.designsystem.MonitorTheme
import dev.monitorcidadao.core.designsystem.a11y.A11yAnnouncer
import dev.monitorcidadao.core.designsystem.component.ChipTone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

/**
 * Cabeçalho do post (chip + data) com fonte grande (a11y §13). Gráficos nativos do Robolectric:
 * sem eles a medição de texto é falsa e o teste não diria nada.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PostCabecalhoLayoutTest {
    @get:Rule
    val rule = createComposeRule()

    private fun render(largura: Dp, fontScale: Float) = rule.setContent {
        val density = LocalDensity.current
        CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
            MonitorTheme(darkTheme = false, reduceMotion = true, screenReaderEnabled = false, announcer = A11yAnnouncer.None) {
                Box(Modifier.width(largura)) {
                    PostCabecalho(
                        tipo = "Apresentou projeto",
                        tone = ChipTone.Neutral,
                        data = "08/03",
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }

    private fun SemanticsNode.textLayout(): TextLayoutResult {
        val layouts = mutableListOf<TextLayoutResult>()
        config[SemanticsActions.GetTextLayoutResult].action!!.invoke(layouts)
        return layouts.single()
    }

    @Test
    fun `fonte 200 por cento em card estreito nao corta nem quebra a data`() {
        // Pior caso da spec: 304 dp úteis (tela de 360 dp), chip mais longo, fonte 2x.
        render(largura = 304.dp, fontScale = 2f)
        val data = rule.onNodeWithTag(PostCardTags.DATA).fetchSemanticsNode()
        val layout = data.textLayout()
        // Com softWrap = false o hasVisualOverflow é sempre true (parágrafo de largura ilimitada); o que
        // vale é: uma linha só e o nó com a largura inteira do texto (nada cortado).
        assertEquals(1, layout.lineCount)
        assertTrue(data.boundsInRoot.width >= layout.multiParagraph.maxIntrinsicWidth - 1f)
        assertTrue(data.boundsInRoot.right <= with(rule.density) { 304.dp.toPx() } + 0.5f)
    }

    @Test
    fun `fonte padrao mantem chip e data na mesma linha, data a direita`() {
        // A tela padrão do Robolectric tem 320 dp; 300 dp fica dentro dela.
        render(largura = 300.dp, fontScale = 1f)
        val data = rule.onNodeWithTag(PostCardTags.DATA).fetchSemanticsNode()
        val layout = data.textLayout()
        assertEquals(1, layout.lineCount)
        assertTrue(data.boundsInRoot.width >= layout.multiParagraph.maxIntrinsicWidth - 1f)
        assertEquals(with(rule.density) { 300.dp.toPx() }, data.boundsInRoot.right, 1f)
        assertTrue(data.boundsInRoot.top < with(rule.density) { 20.dp.toPx() })
    }
}

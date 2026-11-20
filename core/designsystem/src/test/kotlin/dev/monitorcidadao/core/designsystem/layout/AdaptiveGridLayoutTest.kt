package dev.monitorcidadao.core.designsystem.layout

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.unit.dp
import dev.monitorcidadao.core.designsystem.MonitorTheme
import dev.monitorcidadao.core.designsystem.a11y.A11yAnnouncer
import dev.monitorcidadao.core.designsystem.component.StatCard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Cards de uma mesma linha da grade ficam com a mesma altura, mesmo quando só uma legenda quebra. */
@RunWith(RobolectricTestRunner::class)
class AdaptiveGridLayoutTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun `cards da mesma linha tem a mesma altura`() {
        val itens = listOf(
            "curto" to "de presença",
            "longo" to "em emendas indicadas ao orçamento da União em 2027",
        )
        rule.setContent {
            MonitorTheme(darkTheme = false, reduceMotion = true, screenReaderEnabled = false, announcer = A11yAnnouncer.None) {
                Box(Modifier.width(360.dp)) {
                    AdaptiveGrid(itens) { (id, legenda), itemModifier ->
                        StatCard(value = "92%", caption = legenda, accessibilityLabel = id, modifier = itemModifier)
                    }
                }
            }
        }
        val curto = rule.onNodeWithContentDescription("curto").fetchSemanticsNode().boundsInRoot
        val longo = rule.onNodeWithContentDescription("longo").fetchSemanticsNode().boundsInRoot
        // Mesma linha (duas colunas) e mesma altura.
        assertEquals(curto.top, longo.top, 0.5f)
        assertTrue(longo.left > curto.left)
        assertEquals(longo.height, curto.height, 0.5f)
    }
}

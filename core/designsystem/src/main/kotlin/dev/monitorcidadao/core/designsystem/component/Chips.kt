package dev.monitorcidadao.core.designsystem.component

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.monitorcidadao.core.designsystem.Monitor
import dev.monitorcidadao.core.designsystem.MonitorColors
import dev.monitorcidadao.core.designsystem.MonitorRadius
import dev.monitorcidadao.core.designsystem.MonitorSpacing
import dev.monitorcidadao.core.designsystem.MonitorType
import dev.monitorcidadao.core.designsystem.R
import dev.monitorcidadao.core.designsystem.a11y.a11yElement

/** Tom do chip. A cor reforça, nunca é a única informação: o texto sempre diz o estado (a11y §14). */
enum class ChipTone { Neutral, Accent, Gasto }

@Immutable
data class ChipModel(val text: String, val tone: ChipTone = ChipTone.Neutral)

private data class ChipColors(val background: Color, val content: Color)

private fun MonitorColors.chipColors(tone: ChipTone): ChipColors = when (tone) {
    ChipTone.Neutral -> ChipColors(canvas, body)
    ChipTone.Accent -> ChipColors(canvas, accent)
    ChipTone.Gasto -> ChipColors(signalGastoBg, signalGasto)
}

/**
 * Chip informativo (não interativo, por isso sem alvo de 48 dp). Sozinho é texto comum;
 * dentro de um grupo ou cabeçalho de card, quem agrupa decide a fala.
 */
@Composable
fun Chip(
    text: String,
    modifier: Modifier = Modifier,
    tone: ChipTone = ChipTone.Neutral,
    fontWeight: FontWeight = FontWeight.SemiBold,
) {
    val colors = Monitor.colors.chipColors(tone)
    Text(
        text = text,
        style = MonitorType.chip.copy(fontWeight = fontWeight),
        color = colors.content,
        modifier = modifier
            .clip(RoundedCornerShape(MonitorRadius.md))
            .background(colors.background)
            .padding(horizontal = 10.dp, vertical = MonitorSpacing.xs),
    )
}

/**
 * Chips de status lado a lado ("Em exercício · Mandato 2027–2031 · 1º mandato") viram UM elemento
 * com o texto completo (a11y §3). Quebram linha em fonte grande.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StatusChipsGroup(chips: List<ChipModel>, modifier: Modifier = Modifier) {
    val label = chips.joinToString(stringResource(R.string.ds_lista_separador)) { it.text }
    FlowRow(
        modifier = modifier.a11yElement(label),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        chips.forEach { Chip(it.text, tone = it.tone) }
    }
}

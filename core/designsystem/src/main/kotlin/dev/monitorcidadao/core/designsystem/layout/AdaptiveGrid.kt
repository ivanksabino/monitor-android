package dev.monitorcidadao.core.designsystem.layout

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import dev.monitorcidadao.core.designsystem.MonitorSpacing
import dev.monitorcidadao.core.designsystem.a11y.ACCESSIBILITY_FONT_SCALE

/**
 * Regra de refluxo (a11y §13): a grade cai para 1 coluna em tamanho de fonte de acessibilidade
 * ou quando cada coluna ficaria com menos de [minColumnWidthSp] em "sp equivalentes"
 * (largura em dp dividida pelo fontScale — o que cabe de texto, não de pixels).
 */
object GridColumns {
    const val DEFAULT_MIN_COLUMN_WIDTH_SP = 130f

    fun count(
        widthDp: Float,
        fontScale: Float,
        preferred: Int = 2,
        spacingDp: Float = MonitorSpacing.sm.value,
        minColumnWidthSp: Float = DEFAULT_MIN_COLUMN_WIDTH_SP,
    ): Int {
        if (preferred <= 1 || fontScale >= ACCESSIBILITY_FONT_SCALE) return 1
        val columnWidthSp = (widthDp - spacingDp * (preferred - 1)) / preferred / fontScale
        return if (columnWidthSp >= minColumnWidthSp) preferred else 1
    }
}

/**
 * Grade simples (não preguiçosa) para poucos itens, como os cards de Resumo. A ordem de leitura
 * segue a ordem dos itens (linha a linha), igual nas duas formas. Itens de uma linha têm a mesma altura:
 * o [itemContent] recebe um Modifier com `fillMaxHeight()` e DEVE aplicá-lo na raiz do item
 * (ex.: `StatCard(..., modifier = it)`), senão o fundo e a borda ficam com alturas diferentes.
 */
@Composable
fun <T> AdaptiveGrid(
    items: List<T>,
    modifier: Modifier = Modifier,
    preferredColumns: Int = 2,
    spacing: Dp = MonitorSpacing.sm,
    itemContent: @Composable (item: T, modifier: Modifier) -> Unit,
) {
    val fontScale = LocalDensity.current.fontScale
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val columns = GridColumns.count(maxWidth.value, fontScale, preferredColumns, spacing.value)
        Column(verticalArrangement = Arrangement.spacedBy(spacing)) {
            items.chunked(columns).forEach { linha ->
                Row(
                    modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.spacedBy(spacing),
                ) {
                    linha.forEach { item ->
                        Box(Modifier.weight(1f).fillMaxHeight()) { itemContent(item, Modifier.fillMaxHeight()) }
                    }
                    repeat(columns - linha.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

package dev.monitorcidadao.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import dev.monitorcidadao.core.designsystem.Monitor
import dev.monitorcidadao.core.designsystem.MonitorRadius
import dev.monitorcidadao.core.designsystem.MonitorSize
import dev.monitorcidadao.core.designsystem.MonitorSpacing
import dev.monitorcidadao.core.designsystem.MonitorType
import dev.monitorcidadao.core.designsystem.a11y.a11yElement

/**
 * Card de número do Resumo ("92%" + "de presença"). UM elemento com label humano
 * ([accessibilityLabel] = "92% de presença", "112 mil reais da cota em 2027") — a11y §3 e §7.
 */
@Composable
fun StatCard(
    value: String,
    caption: String,
    accessibilityLabel: String,
    modifier: Modifier = Modifier,
) {
    val colors = Monitor.colors
    val shape = RoundedCornerShape(MonitorRadius.card)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.surface)
            .border(MonitorSize.cardBorder, colors.border, shape)
            .padding(horizontal = 14.dp, vertical = MonitorSpacing.md)
            .a11yElement(accessibilityLabel),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(text = value, style = MonitorType.stat, color = colors.ink)
        Text(text = caption, style = MonitorType.caption, color = colors.muted)
    }
}

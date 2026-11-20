package dev.monitorcidadao.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import dev.monitorcidadao.core.designsystem.Monitor
import dev.monitorcidadao.core.designsystem.MonitorSpacing
import dev.monitorcidadao.core.designsystem.MonitorType
import dev.monitorcidadao.core.designsystem.a11y.a11yElement
import dev.monitorcidadao.core.designsystem.format.rememberMonitorFormat

private const val PRESENCE_COLUMNS = 10

/**
 * Grade de presença (modo dados): preenchido = presente, só contorno = falta — a forma distingue,
 * não só a cor (a11y §14). UM elemento com papel de imagem e resumo em texto (a11y §7):
 * "Presente em 18 das últimas 20 sessões; faltou na 6ª e na 15ª".
 */
@Composable
fun PresenceGrid(presencas: List<Boolean>, modifier: Modifier = Modifier) {
    val colors = Monitor.colors
    val resumo = rememberMonitorFormat().resumoPresenca(presencas)
    val shape = RoundedCornerShape(4.dp)
    Column(
        modifier = modifier.fillMaxWidth().a11yElement(resumo, Role.Image),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        presencas.chunked(PRESENCE_COLUMNS).forEach { linha ->
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                linha.forEach { presente ->
                    val cell = Modifier.weight(1f).height(22.dp).clip(shape)
                    Box(
                        if (presente) {
                            cell.background(colors.dataLime)
                        } else {
                            cell.border(1.5.dp, colors.dataOff, shape)
                        },
                    )
                }
                repeat(PRESENCE_COLUMNS - linha.size) { Box(Modifier.weight(1f)) }
            }
        }
    }
}

/**
 * Linha chave/valor mono do card Em números. A chave é técnica (`cota_2027`), então a linha é UM
 * elemento com label humano ([accessibilityLabel] = "Cota parlamentar em 2027: 112 mil reais").
 * Em fonte grande o valor desce para a linha de baixo em vez de cortar.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DataRow(
    chave: String,
    valor: String,
    accessibilityLabel: String,
    modifier: Modifier = Modifier,
    showDivider: Boolean = true,
) {
    val colors = Monitor.colors
    FlowRow(
        modifier = modifier
            .fillMaxWidth()
            .drawBehind {
                if (showDivider) drawLine(colors.dataLine, Offset.Zero, Offset(size.width, 0f), 1.dp.toPx())
            }
            .padding(top = MonitorSpacing.sm)
            .a11yElement(accessibilityLabel),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(chave, style = MonitorType.data, color = colors.dataMuted, modifier = Modifier.padding(end = MonitorSpacing.md))
        Text(valor, style = MonitorType.data, color = colors.dataInk)
    }
}

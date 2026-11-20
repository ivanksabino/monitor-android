package dev.monitorcidadao.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.monitorcidadao.core.designsystem.Monitor
import dev.monitorcidadao.core.designsystem.MonitorRadius
import dev.monitorcidadao.core.designsystem.MonitorSize
import dev.monitorcidadao.core.designsystem.MonitorSpacing
import dev.monitorcidadao.core.designsystem.MonitorTouch
import dev.monitorcidadao.core.designsystem.MonitorType

/** Uma aba: texto localizado e testTag estável (separado do label, a11y §17). */
@Immutable
data class TabOption(val label: String, val testTag: String)

/**
 * Contrato de abas (a11y §5), igual para o segmentado e para as seções roláveis:
 * - contêiner com `selectableGroup()` (o Compose expõe a coleção e a posição "1 de 2");
 * - cada item `selectable(role = Role.Tab)` com o estado selecionado na semântica;
 * - troca idempotente: tocar na aba já selecionada não chama [onSelect] (não recarrega nem anuncia).
 */
private fun Modifier.tabItem(selected: Boolean, index: Int, onSelect: (Int) -> Unit, option: TabOption): Modifier =
    this
        .testTag(option.testTag)
        .selectable(selected = selected, role = Role.Tab, onClick = { if (!selected) onSelect(index) })
        // testTag, nome, papel e selecionado no MESMO nó; o Text filho sai da árvore (BUG-006).
        .clearAndSetSemantics { text = AnnotatedString(option.label) }
        .defaultMinSize(minHeight = MonitorTouch.minTarget)

/** Texto das pílulas de seção: 14/600 (protótipo), derivado do label para seguir o fontScale. */
private val TabLabelStyle = MonitorType.label.copy(
    fontSize = MonitorType.label.fontSize * (14f / 15f),
    fontWeight = FontWeight.SemiBold,
)

/** Texto do segmentado: 14/700 (protótipo). */
private val SegmentLabelStyle = TabLabelStyle.copy(fontWeight = FontWeight.Bold)

/**
 * Segmentado "Resumo | Em números": itens de mesma largura, mínimo 48 dp.
 * Selecionado não depende só de cor (a11y §14, WCAG 1.4.11): além do fundo, borda de 1 dp em
 * accent, que tem mais de 3:1 contra o trilho nos dois temas (claro ≈ 6,7:1; escuro ≈ 5,1:1).
 */
@Composable
fun SegmentedTabs(
    options: List<TabOption>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = Monitor.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(MonitorRadius.md))
            .background(colors.segmentTrack)
            .padding(MonitorSpacing.xs)
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(MonitorSpacing.xs),
    ) {
        options.forEachIndexed { index, option ->
            val selected = index == selectedIndex
            val shape = RoundedCornerShape(MonitorRadius.sm)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .then(if (selected) Modifier.shadow(1.dp, shape) else Modifier)
                    .clip(shape)
                    .background(if (selected) colors.surface else Color.Transparent)
                    .then(if (selected) Modifier.border(MonitorSize.cardBorder, colors.accent, shape) else Modifier)
                    .tabItem(selected, index, onSelect, option)
                    .padding(horizontal = MonitorSpacing.sm, vertical = MonitorSpacing.sm),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = option.label,
                    style = SegmentLabelStyle,
                    color = if (selected) colors.ink else colors.muted,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/** Seções do perfil (Posts, Sobre, Atuação…): pílulas roláveis na horizontal, mínimo 48 dp. */
@Composable
fun SectionTabs(
    options: List<TabOption>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = Monitor.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(MonitorSpacing.xs),
    ) {
        options.forEachIndexed { index, option ->
            val selected = index == selectedIndex
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(MonitorRadius.pill))
                    .background(if (selected) colors.ink else Color.Transparent)
                    .tabItem(selected, index, onSelect, option)
                    // Protótipo: 14 na pílula selecionada, 12 nas demais.
                    .padding(horizontal = if (selected) 14.dp else MonitorSpacing.md),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = option.label,
                    style = TabLabelStyle,
                    color = if (selected) colors.surface else colors.body,
                )
            }
        }
    }
}

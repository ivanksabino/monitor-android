package dev.monitorcidadao.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import dev.monitorcidadao.core.designsystem.Monitor
import dev.monitorcidadao.core.designsystem.MonitorSize
import dev.monitorcidadao.core.designsystem.MonitorSpacing
import dev.monitorcidadao.core.designsystem.MonitorTouch
import dev.monitorcidadao.core.designsystem.MonitorType

private val ButtonShape = RoundedCornerShape(MonitorSize.buttonRadius)
private val ButtonPadding = PaddingValues(horizontal = MonitorSpacing.lg, vertical = MonitorSpacing.md)

/** Aparência comum dos botões de texto: mínimo de 48 dp, texto quebra em vez de cortar (a11y §12, §13). */
@Composable
private fun ButtonSurface(
    text: String,
    background: Color,
    contentColor: Color,
    modifier: Modifier,
    interaction: Modifier,
    icon: ImageVector?,
    /** Com nome explícito no botão, o texto visível não entra de novo na fala. */
    hideContentSemantics: Boolean = false,
) {
    // Ordem: clip ANTES da interação, para o ripple e o realce de foco (teclado, switch access)
    // respeitarem os cantos arredondados.
    Row(
        modifier = modifier
            .defaultMinSize(minHeight = MonitorTouch.minTarget)
            .clip(ButtonShape)
            .then(interaction)
            // Um nó só para o leitor e para a automação (testTag + nome + papel + estado): os filhos
            // saem da árvore. Com filhos semânticos o Compose cria nós "falsos" para o nome e o papel,
            // e o nó do testTag ficava sem nome na árvore (BUG-006). O que vem antes (clickable,
            // toggleable, nome explícito) é preservado.
            .clearAndSetSemantics { if (!hideContentSemantics) this.text = AnnotatedString(text) }
            .background(background)
            .padding(ButtonPadding),
        horizontalArrangement = Arrangement.spacedBy(MonitorSpacing.sm, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            // Ícone ao lado de texto que já diz o mesmo: decorativo (a11y §8).
            Icon(icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(MonitorSize.icon))
        }
        Text(
            text = text,
            style = MonitorType.label,
            color = contentColor,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * Botão principal (accent). [accessibilityLabel] só quando o texto visível precisa de contexto,
 * e deve COMEÇAR com o texto visível (WCAG 2.5.3), ex.: "Tentar de novo carregar o perfil".
 */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    accessibilityLabel: String? = null,
) {
    val colors = Monitor.colors
    ButtonSurface(
        text = text,
        background = if (enabled) colors.accent else colors.segmentTrack,
        contentColor = if (enabled) colors.onAccent else colors.muted,
        icon = icon,
        hideContentSemantics = accessibilityLabel != null,
        modifier = modifier,
        interaction = Modifier
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .labelOverride(accessibilityLabel),
    )
}

/** Botão secundário (preenchido com canvas, como no protótipo), ex.: "Alertas" com sino, "Tentar de novo". */
@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    accessibilityLabel: String? = null,
) {
    val colors = Monitor.colors
    ButtonSurface(
        text = text,
        background = colors.canvas,
        contentColor = if (enabled) colors.ink else colors.muted,
        icon = icon,
        hideContentSemantics = accessibilityLabel != null,
        modifier = modifier,
        interaction = Modifier
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .labelOverride(accessibilityLabel),
    )
}

/**
 * Botão de alternância (ex.: Seguir). O NOME é fixo ([accessibilityLabel], "Seguir Ana Ribeiro") e o
 * estado vai para a semântica ([stateOn]/[stateOff], "seguindo"/"não seguindo") — a11y §6.
 * Visualmente alterna entre [textOff] (primário) e [textOn] (secundário, preenchido com canvas).
 * Desabilitado usa as mesmas cores dos outros botões desabilitados.
 * Tocar não pede anúncio: o foco fica no botão e o leitor fala o novo estado.
 */
@Composable
fun ToggleButton(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    textOff: String,
    textOn: String,
    accessibilityLabel: String,
    stateOn: String,
    stateOff: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = Monitor.colors
    ButtonSurface(
        text = if (checked) textOn else textOff,
        background = when {
            !enabled -> colors.segmentTrack
            checked -> colors.canvas
            else -> colors.accent
        },
        contentColor = when {
            !enabled -> colors.muted
            checked -> colors.ink
            else -> colors.onAccent
        },
        icon = null,
        hideContentSemantics = true,
        modifier = modifier,
        interaction = Modifier
            .toggleable(value = checked, enabled = enabled, role = Role.Button, onValueChange = onCheckedChange)
            .semantics {
                contentDescription = accessibilityLabel
                stateDescription = if (checked) stateOn else stateOff
            },
    )
}

/**
 * Botão redondo só com ícone (Voltar, Compartilhar sobre a capa). [contentDescription] é obrigatório
 * (a11y §7). Alvo de 48 dp.
 */
@Composable
fun CircleIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    background: Color = Monitor.colors.overlayButtonBg,
    tint: Color = Monitor.colors.overlayButtonInk,
) {
    Box(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .size(MonitorSize.circleButton)
            .clip(CircleShape)
            .background(background)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(MonitorSize.icon))
    }
}

private fun Modifier.labelOverride(label: String?): Modifier =
    if (label == null) this else semantics { contentDescription = label }

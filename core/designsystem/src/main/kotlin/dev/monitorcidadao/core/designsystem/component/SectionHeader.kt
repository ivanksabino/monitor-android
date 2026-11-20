package dev.monitorcidadao.core.designsystem.component

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import dev.monitorcidadao.core.designsystem.Monitor
import dev.monitorcidadao.core.designsystem.MonitorType
import dev.monitorcidadao.core.designsystem.a11y.a11yHeading
import dev.monitorcidadao.core.designsystem.format.rememberMonitorFormat

/**
 * Nível do título na hierarquia da tela (a11y §2). O Android não tem níveis em `heading()`:
 * a ordem visual define a hierarquia, e o nível escolhe a tipografia (igual ao `.h1/.h2/.h3` do iOS).
 */
enum class HeadingLevel { H1, H2, H3, Overline }

private fun HeadingLevel.style(): TextStyle = when (this) {
    HeadingLevel.H1 -> MonitorType.title
    HeadingLevel.H2 -> MonitorType.headline
    HeadingLevel.H3 -> MonitorType.cardTitle
    HeadingLevel.Overline -> MonitorType.overline
}

/**
 * Cabeçalho de tela/seção. Toda seção visual relevante tem um, mesmo que seja um overline pequeno.
 * O overline aparece em caixa alta, mas o leitor recebe o texto original (não soletra siglas falsas).
 * [accessibilityLabel] reescreve a fala quando o visual é técnico ou abreviado.
 */
@Composable
fun SectionHeader(
    text: String,
    level: HeadingLevel,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    accessibilityLabel: String? = null,
) {
    val locale = rememberMonitorFormat().locale
    val isOverline = level == HeadingLevel.Overline
    val resolvedColor = when {
        color != Color.Unspecified -> color
        isOverline -> Monitor.colors.muted
        else -> Monitor.colors.ink
    }
    val spoken = accessibilityLabel ?: text.takeIf { isOverline }
    Text(
        text = if (isOverline) text.uppercase(locale) else text,
        style = level.style(),
        color = resolvedColor,
        modifier = if (spoken != null) {
            modifier.a11yHeading(spoken)
        } else {
            modifier.semantics { heading() }
        },
    )
}


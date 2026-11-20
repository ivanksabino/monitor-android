package dev.monitorcidadao.core.designsystem

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/**
 * Fontes da direção Híbrido, embarcadas em `res/font` (OFL, licença em `assets/licenses/fonts-OFL.txt`):
 * Newsreader e Inter são variáveis (eixo `wght`), IBM Plex Mono vem em arquivos estáticos.
 */
@OptIn(ExperimentalTextApi::class)
object MonitorFonts {
    private fun variable(resId: Int, vararg weights: Int): List<Font> = weights.map { weight ->
        Font(
            resId = resId,
            weight = FontWeight(weight),
            variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
        )
    }

    val serif: FontFamily = FontFamily(variable(R.font.newsreader_variable, 400, 600, 700))
    val sans: FontFamily = FontFamily(variable(R.font.inter_variable, 400, 500, 600, 700))
    val mono: FontFamily = FontFamily(
        Font(R.font.ibm_plex_mono_regular, FontWeight.Normal),
        Font(R.font.ibm_plex_mono_medium, FontWeight.Medium),
        Font(R.font.ibm_plex_mono_semibold, FontWeight.SemiBold),
    )
}

/** Tamanho e entrelinha em `sp`: tudo escala com o fontScale do sistema (a11y §13), até 200%. */
private fun style(family: FontFamily, size: Int, weight: Int, lineHeight: Double, tracking: Double = 0.0) = TextStyle(
    fontFamily = family,
    fontSize = size.sp,
    fontWeight = FontWeight(weight),
    lineHeight = (size * lineHeight).sp,
    letterSpacing = tracking.em,
)

/** Estilos de `contracts/design/tokens.json` (mesmos nomes do iOS). */
object MonitorType {
    val display = style(MonitorFonts.serif, 34, 700, 1.05, -0.01)
    val title = style(MonitorFonts.serif, 30, 700, 1.05, -0.01)
    val headline = style(MonitorFonts.serif, 22, 700, 1.15)
    val cardTitle = style(MonitorFonts.serif, 21, 700, 1.15)
    val stat = style(MonitorFonts.serif, 28, 700, 1.1)
    val body = style(MonitorFonts.sans, 16, 400, 1.45) // tokens: 16sp no Compose (bodyLarge)
    val label = style(MonitorFonts.sans, 15, 700, 1.2)
    val caption = style(MonitorFonts.sans, 13, 400, 1.3)
    val overline = style(MonitorFonts.sans, 12, 700, 1.2, 0.08)
    /** Chips (status e tipo do post), 12/600 no protótipo; o tipo do post sobe para 700 no componente. */
    val chip = style(MonitorFonts.sans, 12, 600, 1.3)
    val data = style(MonitorFonts.mono, 13, 500, 1.3)
}

internal val MonitorMaterialTypography = Typography(
    displaySmall = MonitorType.display,
    headlineMedium = MonitorType.title,
    headlineSmall = MonitorType.stat,
    titleLarge = MonitorType.headline,
    bodyLarge = MonitorType.body,
    labelLarge = MonitorType.label,
    bodySmall = MonitorType.caption,
    labelSmall = MonitorType.overline,
)

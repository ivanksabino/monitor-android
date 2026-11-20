package dev.monitorcidadao.core.designsystem

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Cores da direção Híbrido (contracts/design/tokens.json), claro e escuro. Contraste já medido. */
@Immutable
data class MonitorColors(
    val canvas: Color,
    val surface: Color,
    val border: Color,
    val ink: Color,
    val body: Color,
    val muted: Color,
    val accent: Color,
    val onAccent: Color,
    val signalNao: Color,
    val signalGasto: Color,
    val signalGastoBg: Color,
    val graphNeutral: Color,
    val segmentTrack: Color,
    val isDark: Boolean,
    val dataBg: Color = Color(0xFF0D1117),
    val dataInk: Color = Color(0xFFE6EDF3),
    val dataMuted: Color = Color(0xFF9DA7B3),
    val dataLine: Color = Color(0xFF21262D),
    val dataLime: Color = Color(0xFFB8F35A),
    val dataOff: Color = Color(0xFF6E7681),
    /**
     * Tokens do protótipo ainda FORA de contracts/design/tokens.json (contraste não medido no contrato;
     * pedido de inclusão pendente). Nomes alinhados com o iOS quando ele os adotar.
     * Avatar de iniciais (decorativo) e botão redondo sobre a capa (fundo branco translúcido, ícone ink).
     */
    val avatarBg: Color = if (isDark) Color(0xFF26314F) else Color(0xFFE3E8F6),
    val avatarInk: Color = if (isDark) Color(0xFFC7D3FF) else Color(0xFF1A3A8F),
    val avatarBorder: Color = Color(0xFFFFFFFF),
    val overlayButtonBg: Color = Color(0xFFFFFFFF).copy(alpha = 0.94f),
    val overlayButtonInk: Color = Color(0xFF15171A),
) {
    companion object {
        val Light = MonitorColors(
            canvas = Color(0xFFF1F1EE),
            surface = Color(0xFFFFFFFF),
            border = Color(0xFFE2E2DD),
            ink = Color(0xFF15171A),
            body = Color(0xFF3A3D42),
            muted = Color(0xFF5B5F66),
            accent = Color(0xFF1F47C2),
            onAccent = Color(0xFFFFFFFF),
            signalNao = Color(0xFFC2410C),
            signalGasto = Color(0xFF9A3412),
            signalGastoBg = Color(0xFFFDF0E7),
            graphNeutral = Color(0xFF8A8F98),
            segmentTrack = Color(0xFFE6E6E1),
            isDark = false,
        )

        val Dark = MonitorColors(
            canvas = Color(0xFF0F1012),
            surface = Color(0xFF1A1C1F),
            border = Color(0xFF2C2F33),
            ink = Color(0xFFF1F1EE),
            body = Color(0xFFC9CCD1),
            muted = Color(0xFF9A9FA7),
            accent = Color(0xFF7B9BFF),
            onAccent = Color(0xFF0F1012),
            signalNao = Color(0xFFFB8C5A),
            signalGasto = Color(0xFFFDBA8C),
            signalGastoBg = Color(0xFF3A2416),
            graphNeutral = Color(0xFF6B7079),
            segmentTrack = Color(0xFF2C2F33),
            isDark = true,
        )
    }
}

object MonitorSpacing {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 24.dp
}

object MonitorRadius {
    val sm = 9.dp
    val md = 12.dp
    val card = 14.dp
    val lg = 16.dp
    val pill = 22.dp
}

/** Regra do DS: alvo mínimo de 48 dp (mais rígido que os 24 da WCAG 2.5.8). */
object MonitorTouch {
    val minTarget = 48.dp
}

/** Medidas de componente do protótipo (Híbrido). */
object MonitorSize {
    val circleButton = 48.dp
    val avatar = 96.dp
    val avatarSmall = 44.dp
    val icon = 20.dp
    val iconSmall = 16.dp
    val buttonRadius = 10.dp
    val cardBorder = 1.dp
}

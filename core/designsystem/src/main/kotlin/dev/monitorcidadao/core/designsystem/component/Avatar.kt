package dev.monitorcidadao.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalDensity
import dev.monitorcidadao.core.designsystem.Monitor
import dev.monitorcidadao.core.designsystem.MonitorFonts
import dev.monitorcidadao.core.designsystem.MonitorSize
import dev.monitorcidadao.core.designsystem.a11y.decorative
import dev.monitorcidadao.core.imageloader.RemoteImage
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight

/**
 * Avatar de iniciais (com foto opcional por cima). Sempre DECORATIVO (a11y §8): o nome está ao lado.
 * O tamanho das iniciais acompanha o diâmetro (o avatar não cresce com a fonte; o nome ao lado cresce).
 */
@Composable
fun Avatar(
    iniciais: String,
    modifier: Modifier = Modifier,
    size: Dp = MonitorSize.avatar,
    fotoUrl: String? = null,
    borderWidth: Dp = 0.dp,
    borderColor: Color = Monitor.colors.avatarBorder,
) {
    val colors = Monitor.colors
    Box(
        modifier = modifier
            .decorative()
            .size(size)
            .clip(CircleShape)
            .background(colors.avatarBg)
            .let { if (borderWidth > 0.dp) it.border(borderWidth, borderColor, CircleShape) else it },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = iniciais,
            color = colors.avatarInk,
            // Proporcional ao círculo e imune ao fontScale (toSp desfaz a escala): decorativo, não estoura.
            style = TextStyle(
                fontFamily = MonitorFonts.serif,
                fontWeight = FontWeight.Bold,
                fontSize = with(LocalDensity.current) { (size * 0.35f).toSp() },
            ),
        )
        if (fotoUrl != null) {
            RemoteImage(url = fotoUrl, contentDescription = null, modifier = Modifier.fillMaxSize())
        }
    }
}

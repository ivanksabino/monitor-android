package dev.monitorcidadao.core.designsystem

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import dev.monitorcidadao.core.designsystem.a11y.A11yAnnouncer
import dev.monitorcidadao.core.designsystem.a11y.LocalA11yAnnouncer
import dev.monitorcidadao.core.designsystem.a11y.LocalReduceMotion
import dev.monitorcidadao.core.designsystem.a11y.LocalScreenReaderEnabled
import dev.monitorcidadao.core.designsystem.a11y.rememberSystemReduceMotion
import dev.monitorcidadao.core.designsystem.a11y.rememberSystemScreenReaderEnabled
import dev.monitorcidadao.core.designsystem.a11y.rememberViewA11yAnnouncer

val LocalMonitorColors = staticCompositionLocalOf { MonitorColors.Light }

/**
 * Tema do app: Material 3 alimentado pelos mesmos tokens do iOS. Escuro obrigatório desde o início.
 * Também fornece o ambiente de acessibilidade (Reduzir movimento, leitor ativo, anunciador).
 * Os parâmetros opcionais existem para previews e testes; `null` = valor do sistema.
 */
@Composable
fun MonitorTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    reduceMotion: Boolean? = null,
    screenReaderEnabled: Boolean? = null,
    announcer: A11yAnnouncer? = null,
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) MonitorColors.Dark else MonitorColors.Light
    CompositionLocalProvider(
        LocalMonitorColors provides colors,
        LocalReduceMotion provides (reduceMotion ?: rememberSystemReduceMotion()),
        LocalScreenReaderEnabled provides (screenReaderEnabled ?: rememberSystemScreenReaderEnabled()),
        LocalA11yAnnouncer provides (announcer ?: rememberViewA11yAnnouncer()),
    ) {
        MaterialTheme(colorScheme = colors.toColorScheme(), typography = MonitorMaterialTypography, content = content)
    }
}

private fun MonitorColors.toColorScheme(): ColorScheme {
    val base = if (isDark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = accent, onPrimary = onAccent,
        background = canvas, onBackground = ink,
        surface = surface, onSurface = ink,
        surfaceVariant = segmentTrack, onSurfaceVariant = muted,
        outline = border, outlineVariant = border,
        error = signalNao,
    )
}

object Monitor {
    val colors: MonitorColors
        @Composable @ReadOnlyComposable get() = LocalMonitorColors.current
}

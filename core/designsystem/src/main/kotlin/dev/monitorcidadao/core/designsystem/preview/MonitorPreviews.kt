package dev.monitorcidadao.core.designsystem.preview

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import dev.monitorcidadao.core.designsystem.Monitor
import dev.monitorcidadao.core.designsystem.MonitorSpacing
import dev.monitorcidadao.core.designsystem.MonitorTheme
import dev.monitorcidadao.core.designsystem.a11y.A11yAnnouncer

/** Os três cenários obrigatórios de todo componente: claro, escuro e fonte 200% (a11y §13 e §14). */
@Preview(name = "Claro", showBackground = true, widthDp = 390)
@Preview(name = "Escuro", showBackground = true, widthDp = 390, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Fonte 200%", showBackground = true, widthDp = 390, fontScale = 2f)
annotation class MonitorPreviews

/** Tema para previews: segue o uiMode do preview, sem movimento e sem leitor. */
@Composable
fun PreviewSurface(content: @Composable () -> Unit) {
    MonitorTheme(reduceMotion = true, screenReaderEnabled = false, announcer = A11yAnnouncer.None) {
        Box(Modifier.background(Monitor.colors.canvas).padding(MonitorSpacing.md)) { content() }
    }
}

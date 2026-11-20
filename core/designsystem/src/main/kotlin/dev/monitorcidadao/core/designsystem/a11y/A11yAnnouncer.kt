package dev.monitorcidadao.core.designsystem.a11y

import android.view.View
import android.view.accessibility.AccessibilityManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalView

/**
 * Helper ÚNICO de anúncios do app (a11y §10). Anúncio é transição de estado, nunca render:
 * chame em resposta a um evento (ex.: erro ao seguir com estado revertido), não dentro da composição.
 * Se o foco já vai para o conteúdo novo, não anuncie (fala duplicada).
 */
fun interface A11yAnnouncer {
    fun announce(message: String)

    companion object {
        /** Para previews e testes que não verificam anúncios. */
        val None = A11yAnnouncer { }
    }
}

/** Implementação sobre a View raiz. Só fala com leitor de tela ativo. */
class ViewA11yAnnouncer(private val view: View) : A11yAnnouncer {
    private val manager: AccessibilityManager? =
        view.context.getSystemService(AccessibilityManager::class.java)

    override fun announce(message: String) {
        if (message.isBlank() || manager?.isEnabled != true) return
        // Alternativa a liveRegion para eventos sem elemento de status visível (ex.: erro revertido).
        @Suppress("DEPRECATION")
        view.announceForAccessibility(message)
    }
}

/** Fornecido pelo `MonitorTheme`; telas leem daqui e ViewModels recebem o texto já localizado. */
val LocalA11yAnnouncer = staticCompositionLocalOf<A11yAnnouncer> { A11yAnnouncer.None }

@Composable
fun rememberViewA11yAnnouncer(): A11yAnnouncer {
    val view = LocalView.current
    return remember(view) { ViewA11yAnnouncer(view) }
}

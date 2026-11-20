package dev.monitorcidadao.core.designsystem.a11y

import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity

/**
 * "Reduzir movimento" (a11y §15). No Android a preferência é a escala de animação do sistema
 * (Remover animações = 0). O `MonitorTheme` fornece o valor; previews e testes podem sobrescrever.
 */
val LocalReduceMotion = staticCompositionLocalOf { false }

/** Leitor de tela (TalkBack) ativo — usado só para foco programático de exceção (a11y §1 e §4). */
val LocalScreenReaderEnabled = staticCompositionLocalOf { false }

/** Lê a escala de animação do sistema e acompanha mudanças enquanto a tela está viva. */
@Composable
fun rememberSystemReduceMotion(): Boolean {
    val context = LocalContext.current
    var reduce by remember(context) { mutableStateOf(isAnimationOff(context)) }
    DisposableEffect(context) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                reduce = isAnimationOff(context)
            }
        }
        context.contentResolver.registerContentObserver(
            Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE), false, observer,
        )
        onDispose { context.contentResolver.unregisterContentObserver(observer) }
    }
    return reduce
}

/** TalkBack (ou outro serviço de exploração por toque) ligado, acompanhando mudanças. */
@Composable
fun rememberSystemScreenReaderEnabled(): Boolean {
    val context = LocalContext.current
    val manager = remember(context) { context.getSystemService(AccessibilityManager::class.java) }
    var enabled by remember(manager) { mutableStateOf(manager?.isTouchExplorationEnabled == true) }
    DisposableEffect(manager) {
        val listener = AccessibilityManager.TouchExplorationStateChangeListener { enabled = it }
        manager?.addTouchExplorationStateChangeListener(listener)
        onDispose { manager?.removeTouchExplorationStateChangeListener(listener) }
    }
    return enabled
}

private fun isAnimationOff(context: Context): Boolean =
    Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f

/**
 * Tamanho de fonte "de acessibilidade" (a11y §13): a partir daqui grades viram 1 coluna e linhas
 * horizontais viram verticais. 1,3 cobre os passos "Grande"/"Maior" do Android 14+ (até 2,0).
 */
const val ACCESSIBILITY_FONT_SCALE = 1.3f

@Composable
fun isAccessibilityFontScale(): Boolean = LocalDensity.current.fontScale >= ACCESSIBILITY_FONT_SCALE

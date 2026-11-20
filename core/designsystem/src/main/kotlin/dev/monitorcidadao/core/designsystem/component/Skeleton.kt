package dev.monitorcidadao.core.designsystem.component

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import dev.monitorcidadao.core.designsystem.Monitor
import dev.monitorcidadao.core.designsystem.MonitorRadius
import dev.monitorcidadao.core.designsystem.a11y.LocalReduceMotion
import dev.monitorcidadao.core.designsystem.a11y.a11yElement
import dev.monitorcidadao.core.designsystem.a11y.decorative

/** Opacidade compartilhada pelos blocos do mesmo esqueleto (pulsam juntos). */
private val LocalSkeletonAlpha = compositionLocalOf<State<Float>> { mutableFloatStateOf(STATIC_ALPHA) }

private const val STATIC_ALPHA = 0.7f

const val SKELETON_TAG = "skeleton"

/**
 * Esqueleto de carregamento. Com "Reduzir movimento" (a11y §15) fica estático; senão pulsa devagar
 * (sem shimmer). Para o leitor é UM elemento com [label] ("Carregando perfil"); `null` = decorativo.
 */
@Composable
fun SkeletonView(
    label: String?,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val reduceMotion = LocalReduceMotion.current
    val alpha: State<Float> = if (reduceMotion) {
        remember { mutableFloatStateOf(STATIC_ALPHA) }
    } else {
        rememberInfiniteTransition(label = "skeleton").animateFloat(
            initialValue = 0.45f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(durationMillis = 900), RepeatMode.Reverse),
            label = "skeleton-alpha",
        )
    }
    CompositionLocalProvider(LocalSkeletonAlpha provides alpha) {
        Column(
            modifier = modifier
                .testTag(SKELETON_TAG)
                .then(if (label != null) Modifier.a11yElement(label) else Modifier.decorative()),
            content = content,
        )
    }
}

/** Bloco cinza do esqueleto (linha de texto, card, avatar). */
@Composable
fun SkeletonBlock(modifier: Modifier = Modifier, radius: Dp = MonitorRadius.sm) {
    val alpha = LocalSkeletonAlpha.current
    Box(
        modifier
            .graphicsLayer { this.alpha = alpha.value }
            .clip(RoundedCornerShape(radius))
            .background(Monitor.colors.segmentTrack),
    )
}

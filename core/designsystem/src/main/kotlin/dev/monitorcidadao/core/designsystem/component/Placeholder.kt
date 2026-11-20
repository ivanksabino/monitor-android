package dev.monitorcidadao.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.monitorcidadao.core.designsystem.Monitor
import dev.monitorcidadao.core.designsystem.MonitorSpacing
import dev.monitorcidadao.core.designsystem.MonitorType

/**
 * Tela ainda não construída: título (h1) + texto ("Em construção"). Rola em fonte grande.
 * [titleTag] identifica o título para os testes de UI (a11y §17). [navigation] vem antes do título
 * (ex.: o Voltar de uma tela empilhada, primeiro foco da tela, a11y §1). O título também é o
 * `paneTitle`: na mesma janela do Navigation 3, avisa o TalkBack da troca de tela.
 */
@Composable
fun PlaceholderScreen(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    titleTag: String? = null,
    subtitle: String? = null,
    navigation: (@Composable () -> Unit)? = null,
) {
    val colors = Monitor.colors
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.canvas)
            .semantics { paneTitle = title }
            .verticalScroll(rememberScrollState())
            .padding(MonitorSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(MonitorSpacing.sm),
    ) {
        navigation?.invoke()
        SectionHeader(
            text = title,
            level = HeadingLevel.H1,
            modifier = if (titleTag != null) Modifier.testTag(titleTag) else Modifier,
        )
        if (subtitle != null) {
            Text(text = subtitle, style = MonitorType.headline, color = colors.ink)
        }
        Text(text = message, style = MonitorType.body, color = colors.body)
    }
}

/**
 * Cabeçalho só para o leitor de tela (h2 "oculto visualmente", tela 01): entra na navegação por
 * títulos sem ocupar espaço visual. Use quando o protótipo não mostra o título da seção.
 */
@Composable
fun VisuallyHiddenHeading(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(1.dp)
            .semantics {
                contentDescription = text
                heading()
            },
    )
}

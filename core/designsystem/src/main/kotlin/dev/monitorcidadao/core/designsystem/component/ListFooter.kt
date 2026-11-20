package dev.monitorcidadao.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.monitorcidadao.core.designsystem.Monitor
import dev.monitorcidadao.core.designsystem.MonitorSpacing
import dev.monitorcidadao.core.designsystem.MonitorType
import dev.monitorcidadao.core.designsystem.R
import dev.monitorcidadao.core.designsystem.a11y.a11yElement

/** Estado do fim de uma lista paginada. */
enum class ListFooterState { Ocioso, Carregando, Erro, Fim }

object ListFooterTags {
    const val CARREGANDO = "lista_carregando"
    const val TENTAR = "lista_erro_tentar"
    const val FIM = "lista_fim"
}

/**
 * Rodapé de lista paginada (a11y §9): carregar a próxima página não rouba foco nem anuncia
 * (sem live region); erro tem "Tentar de novo" focável no fim da lista; o fim é texto simples.
 * Os textos têm padrão genérico; a tela passa os seus ("Carregando mais posts").
 */
@Composable
fun ListFooter(
    state: ListFooterState,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    loadingLabel: String = stringResource(R.string.ds_lista_carregando),
    errorText: String = stringResource(R.string.ds_lista_erro),
    retryText: String = stringResource(R.string.ds_lista_tentar_de_novo),
    endText: String = stringResource(R.string.ds_lista_fim),
) {
    val colors = Monitor.colors
    Column(
        modifier = modifier.fillMaxWidth().padding(vertical = MonitorSpacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(MonitorSpacing.sm),
    ) {
        when (state) {
            ListFooterState.Ocioso -> Unit
            ListFooterState.Carregando -> CircularProgressIndicator(
                color = colors.accent,
                strokeWidth = 2.5.dp,
                modifier = Modifier
                    .testTag(ListFooterTags.CARREGANDO)
                    .size(28.dp)
                    .a11yElement(loadingLabel),
            )
            ListFooterState.Erro -> {
                Text(errorText, style = MonitorType.body, color = colors.body, textAlign = TextAlign.Center)
                SecondaryButton(
                    text = retryText,
                    onClick = onRetry,
                    modifier = Modifier.testTag(ListFooterTags.TENTAR),
                )
            }
            ListFooterState.Fim -> Text(
                text = endText,
                style = MonitorType.caption,
                color = colors.muted,
                textAlign = TextAlign.Center,
                modifier = Modifier.testTag(ListFooterTags.FIM),
            )
        }
    }
}

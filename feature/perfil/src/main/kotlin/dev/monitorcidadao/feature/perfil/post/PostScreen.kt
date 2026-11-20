package dev.monitorcidadao.feature.perfil.post

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.monitorcidadao.core.designsystem.Monitor
import dev.monitorcidadao.core.designsystem.MonitorSpacing
import dev.monitorcidadao.core.designsystem.MonitorType
import dev.monitorcidadao.core.designsystem.component.CircleIconButton
import dev.monitorcidadao.core.designsystem.component.HeadingLevel
import dev.monitorcidadao.core.designsystem.component.SectionHeader
import dev.monitorcidadao.core.designsystem.icon.MonitorIcons
import dev.monitorcidadao.feature.perfil.R

object PostTags {
    const val TITULO = "post_titulo"
    const val VOLTAR = "post_voltar"
}

@Composable
fun PostRoute(viewModel: PostViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) { viewModel.send(PostViewModel.Action.Apareceu) }
    PostScreen(state, viewModel::send)
}

/**
 * Tela "Post" (placeholder), igual ao iOS (`PostView`): Voltar visível (primeiro foco, a11y §1) com
 * "Post" ao lado como título da barra (rótulo pequeno, não é cabeçalho); o h1 com `post_titulo` é o
 * TÍTULO DO POST ("Post" só enquanto carrega ou se o post não está salvo); depois, "Em construção".
 * O `paneTitle` fica fixo em "Post" para o TalkBack não anunciar a troca de novo quando o título chega.
 */
@Composable
fun PostScreen(state: PostViewModel.State, send: (PostViewModel.Action) -> Unit) {
    val colors = Monitor.colors
    val barra = stringResource(R.string.post_titulo)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.canvas)
            .semantics { paneTitle = barra }
            .verticalScroll(rememberScrollState())
            .padding(MonitorSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(MonitorSpacing.sm),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MonitorSpacing.sm),
        ) {
            CircleIconButton(
                icon = MonitorIcons.Voltar,
                contentDescription = stringResource(R.string.perfil_voltar),
                onClick = { send(PostViewModel.Action.TocouVoltar) },
                modifier = Modifier.testTag(PostTags.VOLTAR),
            )
            Text(text = barra, style = MonitorType.label, color = colors.body)
        }
        SectionHeader(
            text = state.titulo ?: barra,
            level = HeadingLevel.H1,
            modifier = Modifier.testTag(PostTags.TITULO),
        )
        Text(text = stringResource(R.string.post_em_construcao), style = MonitorType.body, color = colors.body)
    }
}

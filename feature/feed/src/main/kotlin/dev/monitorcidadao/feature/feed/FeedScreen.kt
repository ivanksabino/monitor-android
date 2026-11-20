package dev.monitorcidadao.feature.feed

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.monitorcidadao.core.designsystem.Monitor
import dev.monitorcidadao.core.designsystem.MonitorRadius
import dev.monitorcidadao.core.designsystem.MonitorSize
import dev.monitorcidadao.core.designsystem.MonitorSpacing
import dev.monitorcidadao.core.designsystem.MonitorTouch
import dev.monitorcidadao.core.designsystem.MonitorType
import dev.monitorcidadao.core.designsystem.component.Avatar
import dev.monitorcidadao.core.designsystem.component.HeadingLevel
import dev.monitorcidadao.core.designsystem.component.SecondaryButton
import dev.monitorcidadao.core.designsystem.component.SectionHeader
import dev.monitorcidadao.core.designsystem.component.SkeletonBlock
import dev.monitorcidadao.core.designsystem.component.SkeletonView
import dev.monitorcidadao.core.designsystem.format.rememberMonitorFormat
import dev.monitorcidadao.core.designsystem.icon.MonitorIcons
import dev.monitorcidadao.domain.PessoaResumo
import dev.monitorcidadao.feature.feed.FeedViewModel.Action
import dev.monitorcidadao.feature.feed.FeedViewModel.State

object FeedTags {
    const val TITULO = "inicio_titulo"
    const val CARREGANDO = "inicio_carregando"
    const val TENTAR = "inicio_erro_tentar"

    fun pessoa(id: String) = "inicio_pessoa_$id"
}

@Composable
fun FeedRoute(viewModel: FeedViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) { viewModel.send(Action.Apareceu) }
    FeedScreen(state, viewModel::send)
}

/** "Quem você segue" (h1) + uma linha por pessoa; a linha inteira é UM botão. */
@Composable
fun FeedScreen(state: State, send: (Action) -> Unit, modifier: Modifier = Modifier) {
    val colors = Monitor.colors
    val titulo = stringResource(R.string.feed_titulo)
    LazyColumn(
        // paneTitle: na volta de uma tela empilhada o TalkBack recebe a troca de painel (a11y §1).
        modifier = modifier.fillMaxSize().background(colors.canvas).semantics { paneTitle = titulo },
        contentPadding = PaddingValues(MonitorSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(MonitorSpacing.sm),
    ) {
        item(key = "titulo") {
            SectionHeader(
                text = titulo,
                level = HeadingLevel.H1,
                modifier = Modifier.padding(bottom = MonitorSpacing.sm).testTag(FeedTags.TITULO),
            )
        }
        val erro = state.erro
        when {
            state.pessoas.isNotEmpty() -> items(state.pessoas, key = { it.id.valor }) { pessoa ->
                PessoaLinha(pessoa, onClick = { send(Action.TocouPessoa(pessoa.id)) })
            }
            state.carregando -> item(key = "carregando") { FeedCarregando() }
            erro != null -> item(key = "erro") {
                Column(verticalArrangement = Arrangement.spacedBy(MonitorSpacing.md)) {
                    Text(
                        text = stringResource(R.string.feed_erro),
                        style = MonitorType.body,
                        color = colors.body,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    )
                    if (erro.permiteTentarDeNovo) {
                        SecondaryButton(
                            text = stringResource(R.string.feed_tentar_de_novo),
                            onClick = { send(Action.TocouTentarDeNovo) },
                            modifier = Modifier.testTag(FeedTags.TENTAR),
                        )
                    }
                }
            }
            else -> item(key = "vazio") {
                Text(stringResource(R.string.feed_vazio), style = MonitorType.body, color = colors.body)
            }
        }
    }
}

/**
 * Avatar (decorativo) + nome + "Deputada federal · PEX · Paraíba". A linha é UM botão com o nome
 * completo "Ana Ribeiro, Deputada federal, PEX, Paraíba" (a11y §3), mínimo 48 dp.
 */
@Composable
private fun PessoaLinha(pessoa: PessoaResumo, onClick: () -> Unit) {
    val colors = Monitor.colors
    val format = rememberMonitorFormat()
    val falado = format.pessoaFalada(pessoa.nome, pessoa.cargo.texto(), pessoa.partido, pessoa.uf)
    val shape = RoundedCornerShape(MonitorRadius.card)
    Row(
        modifier = Modifier
            .testTag(FeedTags.pessoa(pessoa.id.valor))
            .fillMaxWidth()
            .defaultMinSize(minHeight = MonitorTouch.minTarget)
            .clip(shape)
            .clickable(role = Role.Button, onClick = onClick)
            .clearAndSetSemantics {
                contentDescription = falado
                role = Role.Button
                onClick { onClick(); true }
            }
            .background(colors.surface)
            .border(MonitorSize.cardBorder, colors.border, shape)
            .padding(MonitorSpacing.md),
        horizontalArrangement = Arrangement.spacedBy(MonitorSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(iniciais = pessoa.iniciais, size = MonitorSize.avatarSmall)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(pessoa.nome, style = MonitorType.label, color = colors.ink)
            Text(
                format.subtituloPessoa(pessoa.cargo.texto(), pessoa.partido, pessoa.uf),
                style = MonitorType.caption,
                color = colors.muted,
            )
        }
        Icon(MonitorIcons.ChevronDireita, contentDescription = null, tint = colors.muted, modifier = Modifier.size(MonitorSize.iconSmall))
    }
}

@Composable
private fun FeedCarregando() {
    SkeletonView(
        label = stringResource(R.string.feed_carregando),
        modifier = Modifier.fillMaxWidth().testTag(FeedTags.CARREGANDO),
    ) {
        repeat(3) {
            SkeletonBlock(
                Modifier.padding(bottom = MonitorSpacing.sm).fillMaxWidth().height(68.dp),
                radius = MonitorRadius.card,
            )
        }
    }
}

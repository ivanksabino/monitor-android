package dev.monitorcidadao.core.designsystem.preview

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.monitorcidadao.core.designsystem.Monitor
import dev.monitorcidadao.core.designsystem.MonitorRadius
import dev.monitorcidadao.core.designsystem.MonitorSpacing
import dev.monitorcidadao.core.designsystem.R
import dev.monitorcidadao.core.designsystem.component.Avatar
import dev.monitorcidadao.core.designsystem.component.ChipModel
import dev.monitorcidadao.core.designsystem.component.ChipTone
import dev.monitorcidadao.core.designsystem.component.CircleIconButton
import dev.monitorcidadao.core.designsystem.component.DataRow
import dev.monitorcidadao.core.designsystem.component.HeadingLevel
import dev.monitorcidadao.core.designsystem.component.ListFooter
import dev.monitorcidadao.core.designsystem.component.ListFooterState
import dev.monitorcidadao.core.designsystem.component.PresenceGrid
import dev.monitorcidadao.core.designsystem.component.SecondaryButton
import dev.monitorcidadao.core.designsystem.component.SectionHeader
import dev.monitorcidadao.core.designsystem.component.SectionTabs
import dev.monitorcidadao.core.designsystem.component.SegmentedTabs
import dev.monitorcidadao.core.designsystem.component.SkeletonBlock
import dev.monitorcidadao.core.designsystem.component.SkeletonView
import dev.monitorcidadao.core.designsystem.component.StatCard
import dev.monitorcidadao.core.designsystem.component.StatusChipsGroup
import dev.monitorcidadao.core.designsystem.component.TabOption
import dev.monitorcidadao.core.designsystem.component.ToggleButton
import dev.monitorcidadao.core.designsystem.component.post.PostBloco
import dev.monitorcidadao.core.designsystem.component.post.PostCard
import dev.monitorcidadao.core.designsystem.component.post.PostCardModel
import dev.monitorcidadao.core.designsystem.component.post.PostTipo
import dev.monitorcidadao.core.designsystem.format.rememberMonitorFormat
import dev.monitorcidadao.core.designsystem.icon.MonitorIcons
import dev.monitorcidadao.core.designsystem.layout.AdaptiveGrid
import java.time.LocalDate

// Conteúdo fictício só para preview (pessoa, partido e números inventados). Em produção as
// strings vêm das features; aqui o texto livre é dado de exemplo, não UI do app.

private val presencas = List(20) { it != 5 && it != 14 }

internal val previewPosts = listOf(
    PostCardModel(
        id = "post-9001", tipo = PostTipo.Apresentou, publicadoEm = LocalDate.of(2027, 3, 8),
        titulo = "Quer UBS abertas até as 22h nas cidades maiores",
        corpo = "PL 2210/2027 vale para municípios com mais de 50 mil habitantes. Está na Comissão de Saúde.",
        blocos = listOf(PostBloco.Fonte("Câmara dos Deputados", "https://www.camara.leg.br")),
    ),
    PostCardModel(
        id = "post-9002", tipo = PostTipo.Votou, publicadoEm = LocalDate.of(2027, 3, 10),
        titulo = "Votou sim no PL do transporte escolar rural", corpo = "",
        blocos = listOf(PostBloco.Placar(sim = 312, nao = 140, abstencao = 8), PostBloco.Desconhecido("enquete")),
        temExplicacao = true,
    ),
    PostCardModel(
        id = "post-9003", tipo = PostTipo.Gastou, publicadoEm = LocalDate.of(2027, 3, 11),
        titulo = "Gastou com divulgação do mandato", corpo = "Nota fiscal de gráfica em João Pessoa.",
        blocos = listOf(PostBloco.ValorDestaque(1_850_000, "Cota parlamentar")),
    ),
    PostCardModel(
        id = "post-9004", tipo = PostTipo.Desconhecido, publicadoEm = LocalDate.of(2027, 3, 12),
        titulo = "Tipo novo que o app ainda não conhece", corpo = "Card genérico: título, corpo e fonte.",
        blocos = listOf(PostBloco.Fonte("TSE", null), PostBloco.Placar(1, 2, 3)),
    ),
)

@MonitorPreviews
@Composable
private fun BotoesPreview() = PreviewSurface {
    var seguindo by remember { mutableStateOf(false) }
    val nome = stringResource(R.string.ds_preview_nome)
    Column(verticalArrangement = Arrangement.spacedBy(MonitorSpacing.sm)) {
        Row(Modifier.clip(RoundedCornerShape(MonitorRadius.md)).background(Monitor.colors.accent).padding(8.dp)) {
            CircleIconButton(MonitorIcons.Voltar, contentDescription = "Voltar", onClick = {})
            Box(Modifier.width(8.dp))
            CircleIconButton(MonitorIcons.Compartilhar, contentDescription = "Compartilhar perfil de $nome", onClick = {})
        }
        Row(horizontalArrangement = Arrangement.spacedBy(MonitorSpacing.sm)) {
            ToggleButton(
                checked = seguindo, onCheckedChange = { seguindo = it },
                textOff = "Seguir", textOn = "Seguindo", accessibilityLabel = "Seguir $nome",
                stateOn = "seguindo", stateOff = "não seguindo", modifier = Modifier.weight(1f),
            )
            SecondaryButton("Alertas", onClick = {}, icon = MonitorIcons.Sino, modifier = Modifier.weight(1f))
        }
    }
}

@MonitorPreviews
@Composable
private fun CabecalhoPerfilPreview() = PreviewSurface {
    Column(verticalArrangement = Arrangement.spacedBy(MonitorSpacing.sm)) {
        Avatar(iniciais = "AR")
        SectionHeader(stringResource(R.string.ds_preview_nome), HeadingLevel.H1)
        StatusChipsGroup(
            listOf(
                ChipModel("Em exercício", ChipTone.Accent),
                ChipModel("Mandato 2027–2031"),
                ChipModel("1º mandato"),
            ),
        )
        var segmento by remember { mutableIntStateOf(0) }
        SegmentedTabs(
            options = listOf(TabOption("Resumo", "seg_resumo"), TabOption("Em números", "seg_numeros")),
            selectedIndex = segmento, onSelect = { segmento = it },
        )
        var secao by remember { mutableIntStateOf(0) }
        SectionTabs(
            options = listOf("Posts", "Sobre", "Atuação", "Dinheiro", "Votos", "Imprensa")
                .map { TabOption(it, "secao_$it") },
            selectedIndex = secao, onSelect = { secao = it },
        )
    }
}

@MonitorPreviews
@Composable
private fun ResumoPreview() = PreviewSurface {
    val format = rememberMonitorFormat()
    val stats = listOf(
        Triple("92%", "de presença", "92% de presença"),
        Triple("14", "projetos apresentados", "14 projetos apresentados"),
        Triple(format.moedaCompacta(11_234_018), "da cota em 2027", "${format.moedaPorExtenso(11_234_018)} da cota em 2027"),
        Triple(format.moedaCompacta(840_000_000), "em emendas indicadas", "${format.moedaPorExtenso(840_000_000)} em emendas indicadas"),
    )
    Column(verticalArrangement = Arrangement.spacedBy(MonitorSpacing.sm)) {
        SectionHeader("Resumo do mandato em 2027", HeadingLevel.H2)
        AdaptiveGrid(stats) { (valor, legenda, label), itemModifier -> StatCard(valor, legenda, label, itemModifier) }
    }
}

@MonitorPreviews
@Composable
private fun EmNumerosPreview() = PreviewSurface {
    val format = rememberMonitorFormat()
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(MonitorRadius.lg)).background(Monitor.colors.dataBg).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(MonitorSpacing.sm),
    ) {
        SectionHeader("Presença · últimas 20 sessões", HeadingLevel.Overline, color = Monitor.colors.dataMuted)
        PresenceGrid(presencas)
        DataRow("cota_2027", format.moedaCompleta(11_234_018), "Cota parlamentar em 2027: ${format.moedaPorExtenso(11_234_018)}")
        DataRow("votou_com_partido", format.percentual(94), "Votou com o partido: 94%")
    }
}

@MonitorPreviews
@Composable
private fun PostsPreview() = PreviewSurface {
    Column(verticalArrangement = Arrangement.spacedBy(MonitorSpacing.sm)) {
        previewPosts.forEach { PostCard(it, onVerExplicacao = {}) }
        ListFooter(ListFooterState.Erro, onRetry = {})
        ListFooter(ListFooterState.Fim, onRetry = {})
    }
}

@MonitorPreviews
@Composable
private fun SkeletonPreview() = PreviewSurface {
    SkeletonView(label = stringResource(R.string.ds_carregando)) {
        SkeletonBlock(Modifier.size(96.dp), radius = 48.dp)
        Box(Modifier.height(12.dp))
        SkeletonBlock(Modifier.fillMaxWidth(0.6f).height(28.dp))
        Box(Modifier.height(8.dp))
        SkeletonBlock(Modifier.fillMaxWidth().height(80.dp), radius = MonitorRadius.card)
    }
}

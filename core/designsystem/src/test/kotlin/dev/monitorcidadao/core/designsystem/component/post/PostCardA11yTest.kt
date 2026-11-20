package dev.monitorcidadao.core.designsystem.component.post

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import dev.monitorcidadao.core.designsystem.MonitorTheme
import dev.monitorcidadao.core.designsystem.a11y.A11yAnnouncer
import dev.monitorcidadao.core.designsystem.a11y.assertNoUnico
import dev.monitorcidadao.core.designsystem.a11y.noDeAcessibilidade
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
class PostCardA11yTest {
    @get:Rule
    val rule = createComposeRule()

    private fun render(model: PostCardModel, onVerExplicacao: (String) -> Unit = {}) = rule.setContent {
        MonitorTheme(darkTheme = false, reduceMotion = true, screenReaderEnabled = false, announcer = A11yAnnouncer.None) {
            PostCard(model, onVerExplicacao)
        }
    }

    @Test
    fun `titulo e cabecalho com tipo e data por extenso, chip e data fora da arvore`() {
        render(
            PostCardModel(
                id = "post-9001", tipo = PostTipo.Apresentou, publicadoEm = LocalDate.of(2027, 3, 8),
                titulo = "Quer UBS abertas até as 22h nas cidades maiores",
                corpo = "PL 2210/2027 vale para municípios com mais de 50 mil habitantes.",
                blocos = listOf(PostBloco.Fonte("Câmara dos Deputados", null)),
            ),
        )
        rule.onNodeWithContentDescription(
            "Apresentou projeto, 8 de março de 2027. Quer UBS abertas até as 22h nas cidades maiores",
        ).assert(isHeading())
        rule.onNodeWithText("Apresentou projeto").assertDoesNotExist()
        rule.onNodeWithText("08/03").assertDoesNotExist()
        rule.onNodeWithText("PL 2210/2027 vale para municípios com mais de 50 mil habitantes.").assertExists()
        rule.onNodeWithText("Fonte: Câmara dos Deputados").assertExists()
        rule.onNodeWithTag("post_card_post-9001").assertExists()
    }

    @Test
    fun `link Ver explicacao leva o contexto no nome e navega pelo id`() {
        var aberto: String? = null
        render(
            PostCardModel(
                id = "post-9002", tipo = PostTipo.Votou, publicadoEm = LocalDate.of(2027, 3, 10),
                titulo = "Votou sim no PL do transporte escolar rural", corpo = "",
                blocos = listOf(PostBloco.Placar(312, 140, 8), PostBloco.Desconhecido("enquete")),
                temExplicacao = true,
            ),
            onVerExplicacao = { aberto = it },
        )
        rule.onNodeWithContentDescription("Placar: 312 votos sim, 140 votos não e 8 abstenções").assertExists()
        rule.onNodeWithTag("post_explicacao_post-9002")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.ContentDescription, listOf("Ver explicação: Votou sim no PL do transporte escolar rural")))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            .assertHeightIsAtLeast(48.dp)
            .performClick()
        assertEquals("post-9002", aberto)
        // BUG-006: na árvore real (TalkBack, Trail) o nó do testTag tem o nome e o papel, sem filhos.
        rule.noDeAcessibilidade("post_explicacao_post-9002")
            .assertNoUnico(nome = "Ver explicação: Votou sim no PL do transporte escolar rural", classe = "android.widget.Button")
    }

    @Test
    fun `placar com zero fala no plural`() {
        render(
            PostCardModel(
                id = "post-9005", tipo = PostTipo.Votou, publicadoEm = LocalDate.of(2027, 3, 10),
                titulo = "Votação unânime", corpo = "",
                blocos = listOf(PostBloco.Placar(sim = 300, nao = 0, abstencao = 0)),
            ),
        )
        rule.onNodeWithContentDescription("Placar: 300 votos sim, 0 votos não e 0 abstenções").assertExists()
    }

    @Test
    fun `valor em destaque fala por extenso`() {
        render(
            PostCardModel(
                id = "post-9003", tipo = PostTipo.Gastou, publicadoEm = LocalDate.of(2027, 3, 11),
                titulo = "Gastou com divulgação", corpo = "Nota fiscal.",
                blocos = listOf(PostBloco.ValorDestaque(1_850_000, "Cota parlamentar")),
            ),
        )
        // Abaixo de R$ 100 mil o destaque vai inteiro, igual ao título do post (BUG-008).
        rule.onNodeWithContentDescription("Cota parlamentar: 18.500 reais").assertExists()
        rule.onNodeWithText("R$ 18.500").assertDoesNotExist() // visual fora da árvore: um elemento só
    }

    @Test
    fun `valor em destaque mostra o valor inteiro abaixo de 100 mil e compacto acima`() {
        render(
            PostCardModel(
                id = "post-9006", tipo = PostTipo.Gastou, publicadoEm = LocalDate.of(2027, 3, 11),
                titulo = "Gastou R$ 12.900 com passagens", corpo = "",
                blocos = listOf(PostBloco.ValorDestaque(1_290_000, "gasto"), PostBloco.ValorDestaque(11_234_018, "cota")),
            ),
        )
        rule.onNodeWithContentDescription("gasto: 12.900 reais").assertExists()
        rule.onNodeWithContentDescription("cota: 112 mil reais").assertExists()
    }

    @Test
    fun `tipo desconhecido vira card generico sem chip e sem blocos desconhecidos`() {
        render(
            PostCardModel(
                id = "post-9004", tipo = PostTipo.Desconhecido, publicadoEm = LocalDate.of(2027, 3, 12),
                titulo = "Tipo novo", corpo = "Corpo do post.",
                blocos = listOf(PostBloco.Placar(1, 2, 3), PostBloco.Desconhecido("x"), PostBloco.Fonte("TSE", null)),
                temExplicacao = true,
            ),
        )
        rule.onNodeWithContentDescription("12 de março de 2027. Tipo novo").assert(isHeading())
        rule.onNodeWithText("Corpo do post.").assertExists()
        rule.onNodeWithText("Fonte: TSE").assertExists()
        rule.onNodeWithText("Sim 1").assertDoesNotExist()
        rule.onNodeWithTag("post_explicacao_post-9004").assertDoesNotExist()
    }
}

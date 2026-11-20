package dev.monitorcidadao.feature.perfil

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import dev.monitorcidadao.core.designsystem.MonitorTheme
import dev.monitorcidadao.core.designsystem.a11y.A11yAnnouncer
import dev.monitorcidadao.core.designsystem.component.ListFooterTags
import dev.monitorcidadao.core.designsystem.component.post.PostCardTags
import dev.monitorcidadao.core.routing.Sheet
import dev.monitorcidadao.core.testing.Amostras
import dev.monitorcidadao.core.testing.AmostrasPosts
import dev.monitorcidadao.domain.AppError
import dev.monitorcidadao.feature.perfil.a11y.assertNoUnico
import dev.monitorcidadao.feature.perfil.a11y.noDeAcessibilidade
import dev.monitorcidadao.domain.StatusPagina
import dev.monitorcidadao.feature.perfil.PerfilViewModel.Action
import dev.monitorcidadao.feature.perfil.PerfilViewModel.State
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Semântica da PerfilScreen (telas/01-perfil.md e a11y-nativo.md) com estados fixos: títulos em
 * ordem, Seguir, chips, contrato de abas, identificadores, rodapé da lista e devolução de foco.
 * Tela alta para a LazyColumn compor tudo sem rolar.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h2400dp")
class PerfilScreenA11yTest {
    @get:Rule
    val rule = createComposeRule()

    private val ana = Amostras.pessoa()
    private val posts = AmostrasPosts.paginas(quantas = 1, porPagina = 2).flatten()
    private val conteudo = State(pessoa = ana, posts = posts, seguindo = true)
    private val todas = mutableListOf<Action>()

    /** Ações da pessoa: sem o `ChegouPerto` que cada card dispara ao entrar na tela (paginação). */
    private val acoes: List<Action> get() = todas.filterNot { it is Action.ChegouPerto }

    private fun setContent(state: State, leitorAtivo: Boolean = false, sheet: () -> Sheet? = { null }) = rule.setContent {
        MonitorTheme(darkTheme = false, reduceMotion = true, screenReaderEnabled = leitorAtivo, announcer = A11yAnnouncer.None) {
            PerfilScreen(state = state, send = { todas += it }, sheetAberta = sheet())
        }
    }

    private fun SemanticsNode.falado(): String? =
        config.getOrNull(SemanticsProperties.ContentDescription)?.firstOrNull()
            ?: config.getOrNull(SemanticsProperties.Text)?.firstOrNull()?.text

    private fun titulos(): List<String?> = rule.onAllNodes(isHeading()).fetchSemanticsNodes().map { it.falado() }

    private fun hasRole(role: Role) = SemanticsMatcher.expectValue(SemanticsProperties.Role, role)
    private fun hasState(state: String) = SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, state)
    private fun hasPaneTitle(title: String) = SemanticsMatcher.expectValue(SemanticsProperties.PaneTitle, title)

    // --- Títulos e painel ---

    @Test
    fun `titulos em ordem h1 nome, h2 Resumo, h2 Posts e h3 de cada post`() {
        setContent(conteudo)

        assertEquals(
            listOf(
                "Ana Ribeiro",
                "Resumo do mandato em 2027",
                "Posts",
                "Apresentou projeto, 8 de março de 2027. Título fictício 1",
                "Apresentou projeto, 8 de março de 2027. Título fictício 2",
            ),
            titulos(),
        )
        rule.onNode(hasPaneTitle("Ana Ribeiro")).assertExists()
    }

    @Test
    fun `Em numeros tem h2 proprio antes do titulo da presenca`() {
        setContent(conteudo.copy(segmento = Segmento.EmNumeros))

        val t = titulos()
        val numeros = t.indexOf("Em números do mandato em 2027")
        val presenca = t.indexOf("Presença nas últimas 20 sessões")
        assertTrue("h2 de Em números presente: $t", numeros > 0)
        assertTrue("h2 vem antes do título da presença: $t", numeros < presenca)
        assertTrue("o h2 do Resumo some no outro segmento", "Resumo do mandato em 2027" !in t)
        rule.onNodeWithTag(PerfilTags.PRESENCA).assertExists()
        rule.onNodeWithTag(PerfilTags.stat("presenca")).assertDoesNotExist()
    }

    @Test
    fun `carregando tem Voltar, h1 Perfil e esqueleto como um elemento`() {
        setContent(State(carregando = true))

        rule.onNodeWithTag(PerfilTags.VOLTAR).assert(hasContentDescription("Voltar"))
        assertEquals(listOf("Perfil"), titulos())
        rule.onNodeWithTag(PerfilTags.CARREGANDO).assert(hasContentDescription("Carregando perfil"))
        rule.onNode(hasPaneTitle("Carregando perfil")).assertExists()
    }

    @Test
    fun `erro tem Voltar, h1 Perfil e Tentar de novo`() {
        setContent(State(erro = AppError.SemConexao))

        rule.onNodeWithTag(PerfilTags.VOLTAR).assertExists()
        assertEquals(listOf("Perfil"), titulos())
        rule.onNode(hasPaneTitle("Perfil")).assertExists()
        rule.onNodeWithTag(PerfilTags.ERRO_TENTAR).performClick()
        assertEquals(listOf<Action>(Action.TocouTentarDeNovo), acoes)
    }

    // --- Cabeçalho ---

    @Test
    fun `h1 do nome oferece Atualizar perfil no menu de acoes`() {
        setContent(conteudo)

        val acoesDoNome = rule.onNodeWithTag(PerfilTags.NOME).fetchSemanticsNode()
            .config.getOrNull(SemanticsActions.CustomActions).orEmpty()
        assertEquals(listOf("Atualizar perfil"), acoesDoNome.map { it.label })

        acoesDoNome.single().action()
        assertEquals(listOf<Action>(Action.PuxouParaAtualizar), acoes)
    }

    @Test
    fun `Seguir tem nome fixo e o estado na semantica`() {
        setContent(conteudo)
        rule.onNodeWithTag(PerfilTags.SEGUIR)
            .assert(hasContentDescription("Seguir Ana Ribeiro"))
            .assert(hasState("seguindo"))
            .performClick()
        assertEquals(listOf<Action>(Action.TocouSeguir), acoes)
    }

    @Test
    fun `Seguir desligado anuncia nao seguindo com o mesmo nome`() {
        setContent(conteudo.copy(seguindo = false))
        rule.onNodeWithTag(PerfilTags.SEGUIR)
            .assert(hasContentDescription("Seguir Ana Ribeiro"))
            .assert(hasState("não seguindo"))
    }

    @Test
    fun `chips de status sao um elemento so`() {
        setContent(conteudo)
        rule.onNodeWithContentDescription("Em exercício, Mandato 2027–2031, 1º mandato").assertExists()
        assertEquals(0, rule.onAllNodesWithText("Em exercício").fetchSemanticsNodes().size)
    }

    @Test
    fun `stats do Resumo tem identificadores e texto humano`() {
        setContent(conteudo)
        rule.onNodeWithTag(PerfilTags.stat("presenca")).assert(hasContentDescription("92% de presença"))
        rule.onNodeWithTag(PerfilTags.stat("projetos")).assert(hasContentDescription("14 projetos apresentados"))
        rule.onNodeWithTag(PerfilTags.stat("cota")).assert(hasContentDescription("112 mil reais da cota em 2027"))
        rule.onNodeWithTag(PerfilTags.stat("emendas")).assertExists()
    }

    // --- Abas ---

    @Test
    fun `segmentado segue o contrato de abas e a troca e idempotente`() {
        setContent(conteudo)

        rule.onNodeWithTag(PerfilTags.SEGMENTO_RESUMO).assert(hasRole(Role.Tab)).assertIsSelected().performClick()
        rule.onNodeWithTag(PerfilTags.SEGMENTO_NUMEROS).assert(hasRole(Role.Tab)).assertIsNotSelected().performClick()

        assertEquals(listOf<Action>(Action.TocouSegmento(Segmento.EmNumeros)), acoes)
    }

    @Test
    fun `secoes seguem o contrato de abas com identificadores estaveis`() {
        setContent(conteudo)

        rule.onNodeWithTag(PerfilTags.secao(Secao.Posts)).assert(hasRole(Role.Tab)).assertIsSelected()
        Secao.entries.drop(1).forEach { secao ->
            rule.onNodeWithTag(PerfilTags.secao(secao)).assert(hasRole(Role.Tab)).assertIsNotSelected()
        }
        rule.onNodeWithTag(PerfilTags.secao(Secao.Votos)).performClick()
        assertEquals(listOf<Action>(Action.TocouSecao(Secao.Votos)), acoes)
    }

    // --- Lista ---

    @Test
    fun `Ver explicacao leva o id do post`() {
        setContent(conteudo)
        val comExplicacao = posts.first { it.temExplicacao }
        rule.onNodeWithTag(PostCardTags.explicacao(comExplicacao.id.valor)).performClick()
        assertEquals(listOf<Action>(Action.TocouPost(comExplicacao.id)), acoes)
    }

    @Test
    fun `rodape no fim e no erro de pagina`() {
        var state by mutableStateOf(conteudo.copy(paginacao = StatusPagina.Fim))
        rule.setContent {
            MonitorTheme(darkTheme = false, reduceMotion = true, screenReaderEnabled = false, announcer = A11yAnnouncer.None) {
                PerfilScreen(state = state, send = { todas += it })
            }
        }
        rule.onNodeWithTag(PerfilTags.LISTA).performScrollToNode(hasTestTag(ListFooterTags.FIM))
        rule.onNodeWithTag(ListFooterTags.FIM).assertExists()

        state = conteudo.copy(paginacao = StatusPagina.Erro(AppError.SemConexao, primeiraPagina = false))
        rule.onNodeWithTag(PerfilTags.LISTA).performScrollToNode(hasTestTag(ListFooterTags.TENTAR))
        rule.onNodeWithTag(ListFooterTags.TENTAR).performClick()
        assertEquals(listOf<Action>(Action.TocouTentarDeNovoPagina), acoes)
    }

    @Test
    fun `rodape distingue a primeira carga de carregar mais`() {
        var state by mutableStateOf(conteudo.copy(posts = emptyList(), paginacao = StatusPagina.CarregandoPrimeira))
        rule.setContent {
            MonitorTheme(darkTheme = false, reduceMotion = true, screenReaderEnabled = false, announcer = A11yAnnouncer.None) {
                PerfilScreen(state = state, send = { todas += it })
            }
        }
        rule.onNodeWithTag(ListFooterTags.CARREGANDO).assert(hasContentDescription("Carregando posts"))

        state = conteudo.copy(paginacao = StatusPagina.CarregandoMais)
        rule.onNodeWithTag(ListFooterTags.CARREGANDO).assert(hasContentDescription("Carregando mais posts"))
    }

    // --- BUG-006: na árvore real (TalkBack, Trail) id, nome, papel e estado ficam no MESMO nó ---

    @Test
    fun `Seguir, Alertas, abas e Ver explicacao tem id, nome e papel no mesmo no`() {
        setContent(conteudo)

        val seguir = rule.noDeAcessibilidade(PerfilTags.SEGUIR)
        seguir.assertNoUnico(nome = "Seguir Ana Ribeiro", classe = "android.widget.Button")
        assertEquals("seguindo", seguir.stateDescription?.toString())
        assertTrue(seguir.isCheckable && seguir.isClickable && seguir.isFocusable)

        rule.noDeAcessibilidade(PerfilTags.ALERTAS).assertNoUnico(nome = "Alertas", classe = "android.widget.Button")

        val resumo = rule.noDeAcessibilidade(PerfilTags.SEGMENTO_RESUMO)
        assertEquals("Resumo", resumo.text?.toString())
        assertEquals(0, resumo.childCount)
        assertTrue(resumo.isSelected)
        assertEquals("Em números", rule.noDeAcessibilidade(PerfilTags.SEGMENTO_NUMEROS).text?.toString())

        val votos = rule.noDeAcessibilidade(PerfilTags.secao(Secao.Votos))
        assertEquals("Votos", votos.text?.toString())
        assertEquals(0, votos.childCount)

        val comExplicacao = posts.first { it.temExplicacao }
        rule.noDeAcessibilidade(PostCardTags.explicacao(comExplicacao.id.valor))
            .assertNoUnico(nome = "Ver explicação: ${comExplicacao.titulo}", classe = "android.widget.Button")
    }

    // --- Devolução de foco (a11y §4) ---

    private fun abrirEFecharSheet(sheet: Sheet, leitorAtivo: Boolean) {
        var aberta by mutableStateOf<Sheet?>(null)
        setContent(conteudo, leitorAtivo = leitorAtivo) { aberta }
        rule.waitForIdle()
        aberta = sheet
        rule.waitForIdle()
        aberta = null
        rule.waitForIdle()
    }

    private fun focado(tag: String) =
        rule.onNodeWithTag(tag).fetchSemanticsNode().config.getOrNull(SemanticsProperties.Focused) == true

    /** O atraso de 350 ms do foco corre em tempo real (não no relógio virtual do teste). */
    private fun esperarFoco(tag: String) = rule.waitUntil(timeoutMillis = 3_000) { focado(tag) }

    private fun esperarMaisQueOAtrasoDoFoco() {
        Thread.sleep(700)
        rule.waitForIdle()
    }

    @Test
    fun `com leitor ativo o foco volta para Alertas quando a sheet fecha`() {
        abrirEFecharSheet(Sheet.Alertas(ana.id), leitorAtivo = true)
        esperarFoco(PerfilTags.ALERTAS)
        rule.onNodeWithTag(PerfilTags.ALERTAS).assertIsFocused()
    }

    @Test
    fun `com leitor ativo o foco volta para Compartilhar quando a sheet fecha`() {
        abrirEFecharSheet(Sheet.CompartilharPerfil(ana.id), leitorAtivo = true)
        esperarFoco(PerfilTags.COMPARTILHAR)
        rule.onNodeWithTag(PerfilTags.COMPARTILHAR).assertIsFocused()
    }

    @Test
    fun `sem leitor o foco nao muda quando a sheet fecha`() {
        abrirEFecharSheet(Sheet.Alertas(ana.id), leitorAtivo = false)
        esperarMaisQueOAtrasoDoFoco()
        rule.onNodeWithTag(PerfilTags.ALERTAS).assertIsNotFocused()
    }
}

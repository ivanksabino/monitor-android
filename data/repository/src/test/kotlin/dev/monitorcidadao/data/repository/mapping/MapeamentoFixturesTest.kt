package dev.monitorcidadao.data.repository.mapping

import dev.monitorcidadao.core.network.NetworkJson
import dev.monitorcidadao.core.testing.Fixtures
import dev.monitorcidadao.data.api.dto.PaginaPostsDto
import dev.monitorcidadao.data.api.dto.PessoaDto
import dev.monitorcidadao.data.api.dto.SeguindoDto
import dev.monitorcidadao.domain.AppError
import dev.monitorcidadao.domain.AppException
import dev.monitorcidadao.domain.Cargo
import dev.monitorcidadao.domain.Dinheiro
import dev.monitorcidadao.domain.PessoaId
import dev.monitorcidadao.domain.PostBloco
import dev.monitorcidadao.domain.PostId
import dev.monitorcidadao.domain.PostTipo
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.time.OffsetDateTime
import java.time.ZoneOffset

/** Fixtures reais de contracts/fixtures → entidades de domínio (e ida e volta pelo formato do banco). */
class MapeamentoFixturesTest {

    @Test
    fun `perfil da Ana com centavos, mandato e data ISO com fuso`() {
        val ana = pessoa("cam-1001")

        assertEquals(PessoaId("cam-1001"), ana.id)
        assertEquals(Cargo.DeputadaFederal, ana.cargo)
        assertEquals(Dinheiro(11_234_018), ana.resumo.cota)
        assertEquals(Dinheiro(840_000_000), ana.resumo.emendasIndicadas)
        assertEquals(Dinheiro(124_000_000), ana.numeros.patrimonioTse.valor)
        assertEquals(2026, ana.numeros.patrimonioTse.ano)
        assertEquals(18, ana.numeros.presencas)
        assertEquals(listOf(6, 15), ana.numeros.faltas)
        assertEquals(OffsetDateTime.of(2027, 3, 12, 18, 40, 0, 0, ZoneOffset.ofHours(-3)), ana.atualizadoEm)
    }

    @Test
    fun `senadora e deputado mapeiam o cargo`() {
        assertEquals(Cargo.Senadora, pessoa("sen-2001").cargo)
        assertEquals(Cargo.DeputadoFederal, pessoa("cam-1002").cargo)
    }

    @Test
    fun `cargo desconhecido no perfil e dados invalidos`() {
        val dto = decode<PessoaDto>("pessoa_cam-1001.json").copy(cargo = "ministra")
        assertEquals(AppError.DadosInvalidos, assertThrows<AppException> { dto.paraDominio() }.erro)
    }

    @ParameterizedTest
    @ValueSource(strings = ["cam-1001", "cam-1002", "sen-2001"])
    fun `todas as paginas mapeiam sem perder post e o enquete_futura vira generico`(id: String) {
        val posts = (1..3).flatMap { decode<PaginaPostsDto>("posts_${id}_p$it.json").paraDominio().itens }

        assertEquals(12, posts.size)
        assertTrue(posts.all { it.pessoaId == PessoaId(id) })
        assertEquals(listOf(PostTipo.Desconhecido("enquete_futura")), posts.map { it.tipo }.filterIsInstance<PostTipo.Desconhecido>())
        assertEquals(posts.sortedByDescending { it.publicadoEm }, posts)
    }

    @Test
    fun `bloco desconhecido e descartado e os conhecidos mantem os valores`() {
        val p1 = decode<PaginaPostsDto>("posts_cam-1001_p1.json").paraDominio()

        val discursou = p1.itens.single { it.id == PostId("post-cam-1001-04") }
        assertEquals(PostTipo.Discursou, discursou.tipo)
        assertEquals(listOf(PostBloco.Fonte("Câmara dos Deputados", "https://www.camara.leg.br")), discursou.blocos)

        assertEquals(PostBloco.Placar(312, 140, 0), p1.itens.single { it.id == PostId("post-cam-1001-02") }.blocos.first())
        val gastou = p1.itens.single { it.id == PostId("post-cam-1001-03") }
        assertEquals(PostBloco.ValorDestaque(Dinheiro(3_842_000), "gasto"), gastou.blocos.first())
        assertTrue(gastou.temExplicacao)
        assertEquals("p2", p1.proximoCursor)
    }

    @Test
    fun `post com data invalida e pulado sem derrubar a pagina`() {
        val dto = decode<PaginaPostsDto>("posts_cam-1001_p1.json")
        val estragado = dto.copy(itens = dto.itens.mapIndexed { i, p -> if (i == 0) p.copy(publicadoEm = "ontem") else p })

        assertEquals(3, estragado.paraDominio().itens.size)
    }

    @Test
    fun `seguindo mapeia o resumo das tres pessoas`() {
        val itens = decode<SeguindoDto>("seguindo.json").itens.mapNotNull { it.paraDominioOuNull() }

        assertEquals(listOf("Ana Ribeiro", "Carlos Menezes", "Lúcia Farias"), itens.map { it.nome })
        assertEquals(listOf(Cargo.DeputadaFederal, Cargo.DeputadoFederal, Cargo.Senadora), itens.map { it.cargo })
    }

    @Test
    fun `ida e volta pelo formato do banco preserva pessoa e posts`() {
        val ana = pessoa("cam-1001")
        assertEquals(ana, ana.paraEntidade(salvoEm = 1).paraDominioOuNull())
        assertEquals("2027-03-12T18:40:00-03:00", ana.paraEntidade(salvoEm = 1).atualizadoEm)

        val posts = (1..3).flatMap { decode<PaginaPostsDto>("posts_sen-2001_p$it.json").paraDominio().itens }
        assertEquals(posts, posts.map { it.paraEntidade().paraDominioOuNull() })
    }

    private fun pessoa(id: String) = decode<PessoaDto>("pessoa_$id.json").paraDominio()

    private inline fun <reified T> decode(nome: String): T = NetworkJson.decodeFromString(Fixtures.read(nome))
}

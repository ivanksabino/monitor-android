package dev.monitorcidadao.data.api

import dev.monitorcidadao.core.network.NetworkJson
import dev.monitorcidadao.core.testing.Fixtures
import dev.monitorcidadao.data.api.dto.BlocoDto
import dev.monitorcidadao.data.api.dto.PaginaPostsDto
import dev.monitorcidadao.data.api.dto.PessoaDto
import dev.monitorcidadao.data.api.dto.SeguindoDto
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.junit.jupiter.api.Test

/** Decodifica as fixtures reais de contracts/fixtures (as mesmas que o iOS usa). */
class DtoFixturesTest {

    @ParameterizedTest
    @ValueSource(strings = ["cam-1001", "cam-1002", "sen-2001"])
    fun `todas as paginas de posts decodificam e terminam em cursor nulo`(id: String) {
        val paginas = (1..3).map { decode<PaginaPostsDto>("posts_${id}_p$it.json") }

        assertEquals(listOf("p2", "p3", null), paginas.map { it.proximoCursor })
        assertTrue(paginas.all { p -> p.itens.isNotEmpty() && p.itens.all { it.pessoaId == id } })
        assertEquals(paginas.sumOf { it.itens.size }, paginas.flatMap { it.itens }.map { it.id }.toSet().size)
    }

    @ParameterizedTest
    @ValueSource(strings = ["cam-1001", "cam-1002", "sen-2001"])
    fun `pessoa decodifica com centavos e data em texto ISO`(id: String) {
        val pessoa = decode<PessoaDto>("pessoa_$id.json")

        assertEquals(id, pessoa.id)
        assertEquals(20, pessoa.numeros.presencaUltimas.size)
        assertEquals(pessoa.resumo.cotaCentavos, pessoa.numeros.cotaCentavos)
        assertTrue(pessoa.atualizadoEm.startsWith("2027-"))
    }

    @Test
    fun `tipo desconhecido chega como texto e bloco desconhecido vira Desconhecido`() {
        val p1 = decode<PaginaPostsDto>("posts_cam-1001_p1.json")
        val p2 = decode<PaginaPostsDto>("posts_cam-1001_p2.json")

        val discursou = p1.itens.single { it.id == "post-cam-1001-04" }
        assertEquals(
            listOf(BlocoDto.Fonte("Câmara dos Deputados", "https://www.camara.leg.br"), BlocoDto.Desconhecido("bloco_desconhecido_teste")),
            discursou.blocos,
        )
        assertEquals("enquete_futura", p2.itens.single { it.id == "post-cam-1001-08" }.tipo)
    }

    @Test
    fun `blocos conhecidos decodificam com valores`() {
        val p1 = decode<PaginaPostsDto>("posts_cam-1001_p1.json")

        assertEquals(BlocoDto.Placar(312, 140, 0), p1.itens.single { it.id == "post-cam-1001-02" }.blocos.first())
        assertEquals(BlocoDto.ValorDestaque(3_842_000, "gasto"), p1.itens.single { it.id == "post-cam-1001-03" }.blocos.first())
    }

    @Test
    fun `bloco conhecido malformado nao derruba a pagina`() {
        val json = """{"itens":[{"id":"x","tipo":"votou","pessoa_id":"p","publicado_em":"2027-01-01T00:00:00-03:00",
            "blocos":[{"tipo":"placar","sim":"muitos"},{"semTipo":true},{"tipo":"fonte","texto":"TSE"}]}],"proximo_cursor":null}"""

        val pagina = NetworkJson.decodeFromString(PaginaPostsDto.serializer(), json)

        assertEquals(
            listOf(BlocoDto.Desconhecido("placar"), BlocoDto.Desconhecido(""), BlocoDto.Fonte("TSE", null)),
            pagina.itens.single().blocos,
        )
        assertNull(pagina.proximoCursor)
    }

    @Test
    fun `bloco ida e volta em JSON preserva o tipo`() {
        val blocos = listOf(BlocoDto.Placar(1, 2, 3), BlocoDto.ValorDestaque(10, "gasto"), BlocoDto.Fonte("TSE", null))
        val serializer = kotlinx.serialization.builtins.ListSerializer(BlocoDto.serializer())

        val texto = NetworkJson.encodeToString(serializer, blocos)

        assertTrue(texto.contains("\"tipo\":\"placar\""))
        assertEquals(blocos, NetworkJson.decodeFromString(serializer, texto))
    }

    @Test
    fun `seguindo decodifica a lista fixa`() {
        assertEquals(listOf("cam-1001", "cam-1002", "sen-2001"), decode<SeguindoDto>("seguindo.json").itens.map { it.id })
    }

    private inline fun <reified T> decode(nome: String): T = NetworkJson.decodeFromString(Fixtures.read(nome))
}

package dev.monitorcidadao.core.designsystem.component.post

import dev.monitorcidadao.core.designsystem.component.post.PostCardParte.Bloco
import dev.monitorcidadao.core.designsystem.component.post.PostCardParte.Cabecalho
import dev.monitorcidadao.core.designsystem.component.post.PostCardParte.Corpo
import dev.monitorcidadao.core.designsystem.component.post.PostCardParte.Data
import dev.monitorcidadao.core.designsystem.component.post.PostCardParte.Titulo
import dev.monitorcidadao.core.designsystem.component.post.PostCardParte.VerExplicacao
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate

class PostCardLayoutTest {
    private val fonte = PostBloco.Fonte("Câmara dos Deputados", "https://www.camara.leg.br")
    private val placar = PostBloco.Placar(312, 140, 8)
    private val desconhecido = PostBloco.Desconhecido("enquete")

    private fun post(tipo: PostTipo, blocos: List<PostBloco>, explicacao: Boolean = false, corpo: String = "Corpo") =
        PostCardModel(
            id = "post-1", tipo = tipo, publicadoEm = LocalDate.of(2027, 3, 8),
            titulo = "Título", corpo = corpo, blocos = blocos, temExplicacao = explicacao,
        )

    @Test
    fun `tipo conhecido desenha casca, blocos na ordem e link de explicacao`() {
        val partes = PostCardLayout.partes(post(PostTipo.Votou, listOf(placar, fonte), explicacao = true))
        assertEquals(listOf(Cabecalho, Titulo, Corpo, Bloco(placar), Bloco(fonte), VerExplicacao), partes)
    }

    @Test
    fun `bloco desconhecido e ignorado`() {
        val partes = PostCardLayout.partes(post(PostTipo.Apresentou, listOf(desconhecido, fonte)))
        assertEquals(listOf(Cabecalho, Titulo, Corpo, Bloco(fonte)), partes)
    }

    @Test
    fun `tipo desconhecido vira card generico com titulo, corpo e fonte`() {
        val model = post(PostTipo.Desconhecido, listOf(placar, desconhecido, fonte), explicacao = true)
        assertTrue(PostCardLayout.isGeneric(model))
        assertEquals(listOf(Data, Titulo, Corpo, Bloco(fonte)), PostCardLayout.partes(model))
    }

    @Test
    fun `corpo vazio nao vira parada vazia`() {
        val partes = PostCardLayout.partes(post(PostTipo.Discursou, emptyList(), corpo = " "))
        assertFalse(Corpo in partes)
    }
}

package dev.monitorcidadao.core.designsystem.layout

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class GridColumnsTest {
    @Test
    fun `telefone comum com fonte padrao fica com 2 colunas`() {
        assertEquals(2, GridColumns.count(widthDp = 366f, fontScale = 1f))
        assertEquals(2, GridColumns.count(widthDp = 336f, fontScale = 1.15f))
    }

    @Test
    fun `fonte de acessibilidade vira 1 coluna`() {
        assertEquals(1, GridColumns.count(widthDp = 366f, fontScale = 1.3f))
        assertEquals(1, GridColumns.count(widthDp = 800f, fontScale = 2f))
    }

    @Test
    fun `tela estreita vira 1 coluna mesmo com fonte padrao`() {
        assertEquals(1, GridColumns.count(widthDp = 240f, fontScale = 1f))
    }

    @Test
    fun `preferencia de 1 coluna e respeitada`() {
        assertEquals(1, GridColumns.count(widthDp = 800f, fontScale = 1f, preferred = 1))
    }
}

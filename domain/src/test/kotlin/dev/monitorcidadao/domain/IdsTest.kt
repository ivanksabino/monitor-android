package dev.monitorcidadao.domain

import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class IdsTest {
    @Test
    fun `id serializa como string opaca`() {
        val json = Json.encodeToString(PessoaId.serializer(), PessoaId("cam-1001"))
        assertEquals("\"cam-1001\"", json)
        assertEquals(PessoaId("cam-1001"), Json.decodeFromString(PessoaId.serializer(), json))
    }
}

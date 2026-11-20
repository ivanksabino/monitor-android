package dev.monitorcidadao.core.testing

import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FixturesTest {
    @Test
    fun `fixtures do contrato estao no classpath`() {
        assertTrue(Fixtures.read("seguindo.json").contains("cam-1001"))
        assertTrue(Fixtures.read("posts_cam-1001_p3.json").contains("proximo_cursor"))
        assertNull(Fixtures.readOrNull("nao_existe.json"))
    }

    @Test
    fun `pkcs12 de teste tem a chave do cliente`() {
        val bytes = TestCertificates().clientPkcs12("cliente", "senha".toCharArray())
        val ks = java.security.KeyStore.getInstance("PKCS12").apply { load(bytes.inputStream(), "senha".toCharArray()) }
        assertTrue(ks.isKeyEntry("cliente"))
    }
}

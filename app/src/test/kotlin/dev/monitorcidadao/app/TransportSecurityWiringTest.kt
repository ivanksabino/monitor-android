package dev.monitorcidadao.app

import dev.monitorcidadao.core.network.HTTPClient
import dev.monitorcidadao.core.network.security.TransportSecurity
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.io.File
import okhttp3.CertificatePinner

/**
 * Garante a decisão do MVP: nada no app liga pinning/mTLS.
 * (A configuração que liga existe e é testada em :core:network.)
 * Que o Release não leva fixtures é conferido no próprio APK pela tarefa `verifyReleaseNoFixtures`.
 */
class TransportSecurityWiringTest {
    private val config = appHttpClientConfig(versionName = "teste", logger = null, interceptors = emptyList())

    @Test
    fun `config do app usa TransportSecurity Default e nenhuma identidade`() {
        assertEquals(TransportSecurity.Default, config.transportSecurity)
        assertFalse(config.transportSecurity.isEnabled)
        assertNull(config.identityResolver)
        assertNull(config.trustManager)
    }

    @Test
    fun `cliente montado com a config do app nao tem pinner nem certificado de cliente`() {
        val client = HTTPClient.create(config)
        // O OkHttp copia o pinner com o "chain cleaner" da plataforma: compara os pinos, não a instância.
        assertEquals(CertificatePinner.DEFAULT.pins, client.okHttpClient.certificatePinner.pins)
        assertEquals(CertificatePinner.DEFAULT.pins, (client.imageCallFactory() as okhttp3.OkHttpClient).certificatePinner.pins)
    }

    @Test
    fun `nenhum codigo do app instancia pinning ou identidade de cliente`() {
        val sources = File("src").walkTopDown().filter { it.isFile && it.extension == "kt" && "/test/" !in it.path }
        val proibidos = listOf("PinningPolicy.Spki", "ClientIdentity.Pkcs12", "AndroidKeystoreIdentity")
        sources.forEach { file ->
            val text = file.readText()
            proibidos.forEach { assertFalse(text.contains(it), "${file.path} usa $it") }
        }
    }
}

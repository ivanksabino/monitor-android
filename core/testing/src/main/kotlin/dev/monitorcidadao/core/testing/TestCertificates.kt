package dev.monitorcidadao.core.testing

import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import java.io.ByteArrayOutputStream
import java.security.KeyStore

/**
 * PKI de teste gerada em memória (nada de certificado commitado): uma CA, um certificado de servidor
 * para `localhost` e um certificado de cliente para mTLS.
 */
class TestCertificates {
    val root: HeldCertificate = HeldCertificate.Builder()
        .certificateAuthority(0)
        .commonName("Monitor Cidadão Teste CA")
        .build()

    val server: HeldCertificate = HeldCertificate.Builder()
        .commonName("localhost")
        .addSubjectAlternativeName("localhost")
        .signedBy(root)
        .build()

    val client: HeldCertificate = HeldCertificate.Builder()
        .commonName("app-teste")
        .signedBy(root)
        .build()

    /** Lado servidor do MockWebServer (confia na CA para validar o cliente no mTLS). */
    val serverHandshake: HandshakeCertificates = HandshakeCertificates.Builder()
        .heldCertificate(server, root.certificate)
        .addTrustedCertificate(root.certificate)
        .build()

    /** Lado cliente: confia só na CA de teste. */
    val clientTrust: HandshakeCertificates = HandshakeCertificates.Builder()
        .addTrustedCertificate(root.certificate)
        .build()

    /** Identidade do cliente empacotada como PKCS12 (o formato que o app importa). */
    fun clientPkcs12(alias: String, password: CharArray): ByteArray {
        val keyStore = KeyStore.getInstance("PKCS12").apply { load(null, null) }
        keyStore.setKeyEntry(alias, client.keyPair.private, password, arrayOf(client.certificate, root.certificate))
        return ByteArrayOutputStream().use { out ->
            keyStore.store(out, password)
            out.toByteArray()
        }
    }
}

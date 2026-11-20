package dev.monitorcidadao.core.network

import dev.monitorcidadao.core.network.security.ClientIdentity
import dev.monitorcidadao.core.network.security.ClientIdentityResolver
import dev.monitorcidadao.core.network.security.KeyStoreKeyManager
import dev.monitorcidadao.core.network.security.PinningPolicy
import dev.monitorcidadao.core.network.security.Pkcs12
import dev.monitorcidadao.core.network.security.TransportSecurity
import dev.monitorcidadao.core.network.security.spkiSha256
import dev.monitorcidadao.core.testing.TestCertificates
import dev.monitorcidadao.core.network.interceptor.RetryPolicy
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import java.util.concurrent.atomic.AtomicInteger
import mockwebserver3.MockWebServer
import okhttp3.CertificatePinner
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.concurrent.TimeUnit

/** O pinning e o mTLS estão desligados no app, mas o código é exercitado aqui para não apodrecer. */
class TransportSecurityTest {
    private val certs = TestCertificates()
    private lateinit var server: MockWebServer

    @BeforeEach
    fun setUp() {
        server = MockWebServer().apply {
            useHttps(certs.serverHandshake.sslSocketFactory())
            start()
        }
    }

    @AfterEach
    fun tearDown() = server.close()

    private fun client(security: TransportSecurity, resolver: ClientIdentityResolver? = null) = HTTPClient.create(
        server.config {
            copy(
                transportSecurity = security,
                identityResolver = resolver,
                trustManager = certs.clientTrust.trustManager,
                retryPolicy = null,
            )
        },
    )

    @Nested
    inner class Padrao {
        @Test
        fun `padrao do MVP e tudo desligado`() {
            assertEquals(TransportSecurity(PinningPolicy.None, ClientIdentity.None), TransportSecurity.Default)
            assertFalse(TransportSecurity.Default.isEnabled)
        }

        @Test
        fun `cliente padrao nao instala pinner`() {
            val client = HTTPClient.create(server.config())
            assertTrue(client.okHttpClient.certificatePinner.pins.isEmpty())
        }

        @Test
        fun `Pkcs12 sem resolver falha na montagem`() {
            assertThrows<IllegalStateException> {
                client(TransportSecurity(clientIdentity = ClientIdentity.Pkcs12("x")))
            }
        }
    }

    @Nested
    inner class Pinning {
        @Test
        fun `pino do certificado do servidor aceita`() = runBlocking {
            server.enqueue(ok("seguro"))
            val pin = PinningPolicy.Spki(server.hostName, setOf(certs.server.certificate.spkiSha256()))
            assertEquals(Eco("seguro"), client(TransportSecurity(pinning = pin)).send(ecoEndpoint()))
        }

        @Test
        fun `pino da CA intermediaria ou raiz tambem aceita`() = runBlocking {
            server.enqueue(ok())
            val pin = PinningPolicy.Spki(server.hostName, setOf("AAAA" + "A".repeat(40) + "=", certs.root.certificate.spkiSha256()))
            client(TransportSecurity(pinning = pin)).send(ecoEndpoint())
            assertEquals(1, server.requestCount)
        }

        @Test
        fun `pino errado recusa com NetworkError Security`() = runBlocking {
            server.enqueue(ok())
            val outraChave = TestCertificates().server.certificate.spkiSha256()
            val pin = PinningPolicy.Spki(server.hostName, setOf(outraChave))
            assertThrows<NetworkError.Security> { client(TransportSecurity(pinning = pin)).send(ecoEndpoint()) }
            Unit
        }

        @Test
        fun `pino errado com retry ligado nao repete nem espera`() = runBlocking {
            server.enqueue(ok())
            val sleeper = RecordingSleeper()
            val attempts = AtomicInteger()
            val pin = PinningPolicy.Spki(server.hostName, setOf(TestCertificates().server.certificate.spkiSha256()))
            val client = HTTPClient.create(
                server.config(sleeper) {
                    copy(
                        transportSecurity = TransportSecurity(pinning = pin),
                        trustManager = certs.clientTrust.trustManager,
                        retryPolicy = RetryPolicy(),
                        // Roda depois do RetryInterceptor: conta cada tentativa (o OkHttp pode tentar
                        // mais de uma rota, IPv6/IPv4, dentro de UMA tentativa; isso não é retry nosso).
                        extraInterceptors = listOf(Interceptor { attempts.incrementAndGet(); it.proceed(it.request()) }),
                    )
                },
            )
            assertThrows<NetworkError.Security> { client.send(ecoEndpoint()) }
            assertTrue(sleeper.sleeps.isEmpty(), "falha de pinning não é transitória")
            assertEquals(1, attempts.get())
        }

        @Test
        fun `hash SPKI bate com o formato do OkHttp`() {
            assertEquals("sha256/" + certs.server.certificate.spkiSha256(), CertificatePinner.pin(certs.server.certificate))
        }
    }

    @Nested
    inner class Mtls {
        private val password = "senha-teste".toCharArray()
        private val pkcs12 = certs.clientPkcs12("monitor-cliente", password)
        private val resolver = ClientIdentityResolver { identity ->
            KeyStoreKeyManager(Pkcs12.load(pkcs12, password), identity.keystoreAlias, password)
        }

        @BeforeEach
        fun exigeCertificadoDoCliente() = server.requireClientAuth()

        @Test
        fun `apresenta a identidade PKCS12 e o servidor a recebe`() = runBlocking {
            server.enqueue(ok("mtls"))
            val security = TransportSecurity(clientIdentity = ClientIdentity.Pkcs12("monitor-cliente"))

            assertEquals(Eco("mtls"), client(security, resolver).send(ecoEndpoint()))

            val handshake = server.takeRequest(2, TimeUnit.SECONDS)!!.handshake!!
            assertEquals(certs.client.certificate, handshake.peerCertificates.first())
        }

        @Test
        fun `sem identidade o servidor recusa`() = runBlocking {
            server.enqueue(ok())
            // TLS 1.3: o servidor recusa DEPOIS do handshake do cliente terminar, então o OkHttp vê a
            // conexão ser derrubada (SocketException), não um SSLException.
            assertThrows<NetworkError.NoConnection> { client(TransportSecurity.Default).send(ecoEndpoint()) }
            assertEquals(0, server.requestCount)
        }

        @Test
        fun `pinning e mTLS juntos`() = runBlocking {
            server.enqueue(ok())
            val security = TransportSecurity(
                pinning = PinningPolicy.Spki(server.hostName, setOf(certs.server.certificate.spkiSha256())),
                clientIdentity = ClientIdentity.Pkcs12("monitor-cliente"),
            )
            assertTrue(security.isEnabled)
            client(security, resolver).send(ecoEndpoint())
            assertEquals(1, server.requestCount)
        }

        @Test
        fun `alias inexistente falha cedo`() {
            assertThrows<IllegalArgumentException> { KeyStoreKeyManager(Pkcs12.load(pkcs12, password), "outro", password) }
            assertEquals("monitor-cliente", Pkcs12.keyAlias(Pkcs12.load(pkcs12, password)))
        }
    }
}

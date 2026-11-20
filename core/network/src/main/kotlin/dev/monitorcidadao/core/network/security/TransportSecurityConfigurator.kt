package dev.monitorcidadao.core.network.security

import okhttp3.CertificatePinner
import okhttp3.OkHttpClient
import java.security.KeyStore
import java.security.MessageDigest
import java.security.cert.X509Certificate
import java.util.Base64
import javax.net.ssl.KeyManager
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509KeyManager
import javax.net.ssl.X509TrustManager

/** Resolve a identidade mTLS em um `KeyManager` (Android Keystore no app; PKCS12 em memória no teste). */
fun interface ClientIdentityResolver {
    fun keyManager(identity: ClientIdentity.Pkcs12): X509KeyManager
}

/** Hash SPKI no formato aceito por [PinningPolicy.Spki] (SHA-256 base64 da chave pública). */
fun X509Certificate.spkiSha256(): String =
    Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-256").digest(publicKey.encoded))

/**
 * Aplica [security] ao builder. Com [TransportSecurity.Default] não muda nada, exceto quando
 * [trustManager] é passado (só em teste, para confiar numa CA de teste).
 */
fun OkHttpClient.Builder.applyTransportSecurity(
    security: TransportSecurity,
    identityResolver: ClientIdentityResolver? = null,
    trustManager: X509TrustManager? = null,
): OkHttpClient.Builder = apply {
    (security.pinning as? PinningPolicy.Spki)?.let { policy ->
        certificatePinner(
            CertificatePinner.Builder()
                .apply { policy.hashes.forEach { add(policy.host, "sha256/$it") } }
                .build(),
        )
    }

    val keyManager = (security.clientIdentity as? ClientIdentity.Pkcs12)?.let { identity ->
        checkNotNull(identityResolver) { "ClientIdentity.Pkcs12 exige um ClientIdentityResolver" }
            .keyManager(identity)
    }
    if (keyManager != null || trustManager != null) {
        val trust = trustManager ?: systemTrustManager()
        val context = SSLContext.getInstance("TLS").apply {
            init(keyManager?.let { arrayOf<KeyManager>(it) }, arrayOf(trust), null)
        }
        sslSocketFactory(context.socketFactory, trust)
    }
}

internal fun systemTrustManager(): X509TrustManager {
    val factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
    factory.init(null as KeyStore?)
    return factory.trustManagers.filterIsInstance<X509TrustManager>().first()
}

package dev.monitorcidadao.core.network.security

import java.net.Socket
import java.security.KeyStore
import java.security.Principal
import java.security.PrivateKey
import java.security.cert.X509Certificate
import javax.net.ssl.SSLEngine
import javax.net.ssl.X509ExtendedKeyManager

/**
 * `KeyManager` de cliente que sempre apresenta a entrada [alias] do [keyStore].
 * Funciona com PKCS12 em memória e com o Android Keystore (chave não exportável).
 */
class KeyStoreKeyManager(
    private val keyStore: KeyStore,
    private val alias: String,
    private val password: CharArray? = null,
) : X509ExtendedKeyManager() {

    init {
        require(keyStore.isKeyEntry(alias)) { "Alias '$alias' não tem chave privada no KeyStore" }
    }

    override fun getClientAliases(keyType: String?, issuers: Array<out Principal>?): Array<String> = arrayOf(alias)

    override fun chooseClientAlias(keyType: Array<out String>?, issuers: Array<out Principal>?, socket: Socket?): String =
        alias

    override fun chooseEngineClientAlias(keyType: Array<out String>?, issuers: Array<out Principal>?, engine: SSLEngine?): String =
        alias

    override fun getCertificateChain(alias: String?): Array<X509Certificate>? =
        keyStore.getCertificateChain(alias)?.map { it as X509Certificate }?.toTypedArray()

    override fun getPrivateKey(alias: String?): PrivateKey? = keyStore.getKey(alias, password) as? PrivateKey

    override fun getServerAliases(keyType: String?, issuers: Array<out Principal>?): Array<String>? = null

    override fun chooseServerAlias(keyType: String?, issuers: Array<out Principal>?, socket: Socket?): String? = null
}

/** Utilitários de PKCS12 (JVM puro, testável). */
object Pkcs12 {
    fun load(bytes: ByteArray, password: CharArray): KeyStore =
        KeyStore.getInstance("PKCS12").apply { load(bytes.inputStream(), password) }

    /** Primeiro alias com chave privada dentro do PKCS12. */
    fun keyAlias(keyStore: KeyStore): String =
        keyStore.aliases().toList().first { keyStore.isKeyEntry(it) }
}

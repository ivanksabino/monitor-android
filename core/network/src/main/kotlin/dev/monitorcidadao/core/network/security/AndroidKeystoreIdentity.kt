package dev.monitorcidadao.core.network.security

import android.security.keystore.KeyProperties
import android.security.keystore.KeyProtection
import java.security.KeyStore
import java.security.PrivateKey

/**
 * mTLS com a identidade guardada no Android Keystore (chave privada não sai do hardware).
 * Pronto e desligado: nada no app chama [import] nem usa [resolver] enquanto o MVP roda sem mTLS.
 */
object AndroidKeystoreIdentity {
    private const val PROVIDER = "AndroidKeyStore"

    private fun androidKeyStore(): KeyStore = KeyStore.getInstance(PROVIDER).apply { load(null) }

    val resolver = ClientIdentityResolver { identity ->
        KeyStoreKeyManager(androidKeyStore(), identity.keystoreAlias)
    }

    /** Importa um PKCS12 (ex.: entregue no provisionamento) para o Keystore sob [alias]. */
    fun import(pkcs12: ByteArray, password: CharArray, alias: String) {
        val source = Pkcs12.load(pkcs12, password)
        val sourceAlias = Pkcs12.keyAlias(source)
        val key = source.getKey(sourceAlias, password) as PrivateKey
        val chain = source.getCertificateChain(sourceAlias)
        val protection = KeyProtection.Builder(KeyProperties.PURPOSE_SIGN)
            .setDigests(KeyProperties.DIGEST_NONE, KeyProperties.DIGEST_SHA256, KeyProperties.DIGEST_SHA384, KeyProperties.DIGEST_SHA512)
            .setSignaturePaddings(KeyProperties.SIGNATURE_PADDING_RSA_PKCS1, KeyProperties.SIGNATURE_PADDING_RSA_PSS)
            .build()
        androidKeyStore().setEntry(alias, KeyStore.PrivateKeyEntry(key, chain), protection)
    }
}

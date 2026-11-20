package dev.monitorcidadao.core.storage

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import java.security.GeneralSecurityException
import java.security.KeyStore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Segredos (token etc.). Valores cifrados com chave do Android Keystore e guardados em DataStore. */
interface SecretStore {
    suspend fun get(name: String): String?
    suspend fun put(name: String, value: String)
    suspend fun remove(name: String)
}

interface SecretCipher {
    fun encrypt(plain: ByteArray): ByteArray
    fun decrypt(data: ByteArray): ByteArray
}

/** AES-256-GCM; o IV (12 bytes) vai na frente do texto cifrado. */
class AesGcmSecretCipher(private val key: () -> SecretKey) : SecretCipher {
    override fun encrypt(plain: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, key()) }
        return cipher.iv + cipher.doFinal(plain)
    }

    override fun decrypt(data: ByteArray): ByteArray {
        require(data.size > IV_SIZE) { "dado cifrado curto demais" }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(TAG_BITS, data, 0, IV_SIZE))
        return cipher.doFinal(data, IV_SIZE, data.size - IV_SIZE)
    }

    private companion object {
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_SIZE = 12
        const val TAG_BITS = 128
    }
}

class EncryptedSecretStore(
    private val dataStore: DataStore<Preferences>,
    private val cipher: SecretCipher,
) : SecretStore {
    override suspend fun get(name: String): String? {
        val stored = dataStore.data.first()[key(name)] ?: return null
        return try {
            cipher.decrypt(Base64.getDecoder().decode(stored)).decodeToString()
        } catch (_: GeneralSecurityException) {
            remove(name) // chave trocada/reinstalação: o segredo não serve mais
            null
        } catch (_: IllegalArgumentException) {
            remove(name)
            null
        }
    }

    override suspend fun put(name: String, value: String) {
        val encrypted = Base64.getEncoder().encodeToString(cipher.encrypt(value.encodeToByteArray()))
        dataStore.edit { it[key(name)] = encrypted }
    }

    override suspend fun remove(name: String) {
        dataStore.edit { it.remove(key(name)) }
    }

    private fun key(name: String) = stringPreferencesKey("secret.$name")
}

/** Chave AES gerada e guardada no Android Keystore (não exportável). */
object AndroidKeystoreSecretKey {
    private const val PROVIDER = "AndroidKeyStore"

    fun getOrCreate(alias: String = "monitor_secret_store"): SecretKey {
        val keyStore = KeyStore.getInstance(PROVIDER).apply { load(null) }
        (keyStore.getKey(alias, null) as? SecretKey)?.let { return it }
        val spec = android.security.keystore.KeyGenParameterSpec.Builder(
            alias,
            android.security.keystore.KeyProperties.PURPOSE_ENCRYPT or android.security.keystore.KeyProperties.PURPOSE_DECRYPT,
        )
            .setBlockModes(android.security.keystore.KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(android.security.keystore.KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()
        return KeyGenerator.getInstance(android.security.keystore.KeyProperties.KEY_ALGORITHM_AES, PROVIDER)
            .apply { init(spec) }
            .generateKey()
    }
}

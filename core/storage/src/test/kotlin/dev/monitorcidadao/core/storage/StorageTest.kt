package dev.monitorcidadao.core.storage

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import app.cash.turbine.test
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.util.Base64
import javax.crypto.KeyGenerator

class StorageTest {
    @TempDir
    lateinit var dir: File

    private fun TestScope.dataStore(name: String): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(scope = backgroundScope) { File(dir, "$name.preferences_pb") }

    @Test
    fun `preferencia tipada usa padrao, grava e observa`() = runTest {
        val store = DataStorePreferencesStore(dataStore("prefs"))
        store.observe(AppPreferences.tema).test {
            assertEquals(TemaPreferido.Sistema, awaitItem())
            store.set(AppPreferences.tema, TemaPreferido.Escuro)
            assertEquals(TemaPreferido.Escuro, awaitItem())
            store.remove(AppPreferences.tema)
            assertEquals(TemaPreferido.Sistema, awaitItem())
        }
        store.set(PreferenceKey.int("contador", 0), 3)
        assertEquals(3, store.get(PreferenceKey.int("contador", 0)))
        assertEquals(true, store.get(PreferenceKey.boolean("flag", true)))
    }

    @Test
    fun `enum desconhecido volta ao padrao`() = runTest {
        val ds = dataStore("enum")
        ds.edit { it[stringPreferencesKey("tema")] = "Sepia" }
        assertEquals(TemaPreferido.Sistema, DataStorePreferencesStore(ds).get(AppPreferences.tema))
    }

    private val key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()

    @Test
    fun `segredo e gravado cifrado e lido de volta`() = runTest {
        val ds = dataStore("segredos")
        val store = EncryptedSecretStore(ds, AesGcmSecretCipher { key })

        store.put("token", "segredo-ficticio-123")

        val raw = ds.data.first()[stringPreferencesKey("secret.token")]!!
        assertFalse(String(Base64.getDecoder().decode(raw)).contains("segredo-ficticio-123"))
        assertEquals("segredo-ficticio-123", store.get("token"))
        store.remove("token")
        assertNull(store.get("token"))
    }

    @Test
    fun `segredo com outra chave e descartado`() = runTest {
        val ds = dataStore("rotacao")
        EncryptedSecretStore(ds, AesGcmSecretCipher { key }).put("token", "x")
        val outraChave = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        val store = EncryptedSecretStore(ds, AesGcmSecretCipher { outraChave })
        assertNull(store.get("token"))
        assertNull(ds.data.first()[stringPreferencesKey("secret.token")])
    }

    @Test
    fun `cifra usa IV novo a cada gravacao`() {
        val cipher = AesGcmSecretCipher { key }
        val a = cipher.encrypt("igual".encodeToByteArray())
        val b = cipher.encrypt("igual".encodeToByteArray())
        assertFalse(a.contentEquals(b))
        assertEquals("igual", cipher.decrypt(a).decodeToString())
    }
}

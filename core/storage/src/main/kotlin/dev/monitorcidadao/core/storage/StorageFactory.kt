package dev.monitorcidadao.core.storage

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile

/** Cria os stores do app. Chamar uma única vez (no `AppContainer`): DataStore exige instância única por arquivo. */
object StorageFactory {
    fun preferences(context: Context): PreferencesStore =
        DataStorePreferencesStore(dataStore(context, "preferencias"))

    fun secrets(context: Context): SecretStore =
        EncryptedSecretStore(
            dataStore(context, "segredos"),
            AesGcmSecretCipher { AndroidKeystoreSecretKey.getOrCreate() },
        )

    private fun dataStore(context: Context, name: String): DataStore<Preferences> =
        PreferenceDataStoreFactory.create { context.applicationContext.preferencesDataStoreFile(name) }
}

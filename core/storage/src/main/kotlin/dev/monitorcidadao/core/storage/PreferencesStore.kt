package dev.monitorcidadao.core.storage

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** Chave tipada: nome, valor padrão e conversão de/para o tipo guardado no DataStore. */
class PreferenceKey<T> private constructor(
    internal val raw: Preferences.Key<Any>,
    val default: T,
    private val decode: (Any) -> T?,
    private val encode: (T) -> Any,
) {
    val name: String get() = raw.name

    internal fun read(prefs: Preferences): T = prefs[raw]?.let(decode) ?: default
    internal fun write(value: T): Any = encode(value)

    companion object {
        @Suppress("UNCHECKED_CAST")
        private fun <S : Any> Preferences.Key<S>.erased() = this as Preferences.Key<Any>

        fun boolean(name: String, default: Boolean) =
            PreferenceKey(booleanPreferencesKey(name).erased(), default, { it as? Boolean }, { it })

        fun int(name: String, default: Int) =
            PreferenceKey(intPreferencesKey(name).erased(), default, { it as? Int }, { it })

        fun string(name: String, default: String) =
            PreferenceKey(stringPreferencesKey(name).erased(), default, { it as? String }, { it })

        /** Enum guardado pelo nome; valor desconhecido (enum renomeado) volta ao padrão. */
        inline fun <reified E : Enum<E>> enum(name: String, default: E): PreferenceKey<E> =
            enumKey(name, default, enumValues<E>().associateBy { it.name })

        @PublishedApi
        internal fun <E : Enum<E>> enumKey(name: String, default: E, byName: Map<String, E>) =
            PreferenceKey(stringPreferencesKey(name).erased(), default, { byName[it as? String] }, { it.name })
    }
}

/** Preferências do app (não sensíveis). Segredo vai para o [SecretStore]. */
interface PreferencesStore {
    fun <T> observe(key: PreferenceKey<T>): Flow<T>
    suspend fun <T> get(key: PreferenceKey<T>): T
    suspend fun <T> set(key: PreferenceKey<T>, value: T)
    suspend fun remove(key: PreferenceKey<*>)
}

class DataStorePreferencesStore(private val dataStore: DataStore<Preferences>) : PreferencesStore {
    override fun <T> observe(key: PreferenceKey<T>): Flow<T> =
        dataStore.data.map(key::read).distinctUntilChanged()

    override suspend fun <T> get(key: PreferenceKey<T>): T = observe(key).first()

    override suspend fun <T> set(key: PreferenceKey<T>, value: T) {
        dataStore.edit { it[key.raw] = key.write(value) }
    }

    override suspend fun remove(key: PreferenceKey<*>) {
        dataStore.edit { it.remove(key.raw) }
    }
}

enum class TemaPreferido { Sistema, Claro, Escuro }

/** Catálogo das preferências do app. */
object AppPreferences {
    val tema = PreferenceKey.enum("tema", TemaPreferido.Sistema)
    val ultimaAba = PreferenceKey.string("ultima_aba", "inicio")
}

package dev.monitorcidadao.core.featureflags

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/** Flags conhecidas, com chave estável e valor padrão. */
enum class FeatureFlag(val key: String, val default: Boolean) {
    CompartilharPost("compartilhar_post", false),
    AbaExplorar("aba_explorar", false),

    /** Só Debug: fixtures respondem 503 em toda chamada. */
    FixtureModoFalha("debug_fixture_modo_falha", false),
}

interface FeatureFlags {
    fun isEnabled(flag: FeatureFlag): Boolean
    fun observe(flag: FeatureFlag): Flow<Boolean>
}

/** Provedor local (padrões + sobrescritas em memória). Um provedor remoto entra atrás da mesma interface. */
class LocalFeatureFlags(overrides: Map<FeatureFlag, Boolean> = emptyMap()) : FeatureFlags {
    private val values = MutableStateFlow(overrides)

    override fun isEnabled(flag: FeatureFlag): Boolean = values.value[flag] ?: flag.default

    override fun observe(flag: FeatureFlag): Flow<Boolean> =
        values.map { it[flag] ?: flag.default }.distinctUntilChanged()

    fun set(flag: FeatureFlag, enabled: Boolean) = values.update { it + (flag to enabled) }

    fun reset(flag: FeatureFlag) = values.update { it - flag }
}

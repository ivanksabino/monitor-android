package dev.monitorcidadao.core.network

import kotlinx.serialization.json.Json

/** Json tolerante a evolução do contrato: campo novo não quebra o app. */
val NetworkJson: Json = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    coerceInputValues = true
}

package dev.monitorcidadao.core.network

import okhttp3.Headers
import java.time.Instant
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/** Lê `Retry-After` em segundos ou em data HTTP (RFC 9110 §10.2.3). */
internal object RetryAfter {
    fun parse(headers: Headers, now: Instant = Instant.now()): Duration? {
        val raw = headers["Retry-After"]?.trim() ?: return null
        raw.toLongOrNull()?.let { return if (it >= 0) it.seconds else null }
        return runCatching {
            val date = ZonedDateTime.parse(raw, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant()
            (date.toEpochMilli() - now.toEpochMilli()).coerceAtLeast(0).milliseconds
        }.getOrNull()
    }
}

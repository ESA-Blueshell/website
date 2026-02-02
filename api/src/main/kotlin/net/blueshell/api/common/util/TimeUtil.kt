package net.blueshell.api.common.util

import java.sql.Timestamp
import java.time.Instant
import java.time.LocalDateTime

object TimeUtil {
    fun hasExpired(timestamp: Timestamp?): Boolean {
        if (timestamp == null) return false

        return timestamp.before(Timestamp.from(Instant.now()))
    }

    fun of(localDateTime: LocalDateTime?): Timestamp? {
        if (localDateTime == null) return null

        return Timestamp.valueOf(localDateTime)
    }
}

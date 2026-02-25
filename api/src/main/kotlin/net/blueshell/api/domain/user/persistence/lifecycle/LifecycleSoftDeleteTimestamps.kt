package net.blueshell.api.domain.user.persistence.lifecycle

import java.time.Instant

object LifecycleSoftDeleteTimestamps {
    val ACTIVE_ROW_DELETED_AT: Instant = Instant.parse("9999-12-31T23:59:59Z")
}

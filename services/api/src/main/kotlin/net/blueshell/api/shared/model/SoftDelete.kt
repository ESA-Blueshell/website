package net.blueshell.api.shared.model

import java.time.Instant

/**
 * The soft-delete scheme, written once. A live row's `deleted_at` holds [LIVE] rather than NULL:
 * a unique key over a nullable column admits any number of NULLs, and MariaDB has no partial
 * index to narrow it to live rows (architecture ADR-005).
 */
object SoftDelete {
    /** What `deleted_at` holds while a row is live. */
    const val LIVE = "9999-12-31 23:59:59"

    /** The predicate a live row answers to. */
    const val ACTIVE = "deleted_at = '$LIVE'"

    /** What a soft delete writes: to the microsecond, which a `datetime` column keeps to the second. */
    const val STAMP = "deleted_at = NOW(6)"

    /** [LIVE] as an instant, for an entity that sets `deleted_at` itself. */
    val LIVE_INSTANT: Instant = Instant.parse(LIVE.replace(' ', 'T') + "Z")
}

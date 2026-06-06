package net.blueshell.api.shared.enums

/**
 * External system this app pushes aggregate state to. Persisted as a string in
 * `external_id_mapping.system`.
 *
 * Adding a new target (e.g. `GOOGLE_WORKSPACE`, `DISCORD`) is one new enum
 * value here plus one new `SyncTarget` implementation; the fan-out driver and
 * the mapping table need no further change.
 */
enum class TargetSystem {
    BREVO,
    GOOGLE_CALENDAR,
    ;

    companion object {
        /**
         * Parses a persisted/transport string into the enum, preserving the
         * `valueOf` error semantics the String→enum call sites relied on:
         * an unknown value throws [IllegalArgumentException]. Use only at
         * genuine String boundaries (queue payloads, foreign entities); the
         * typed `Cohort.system` column no longer needs this.
         */
        fun fromPersisted(raw: String): TargetSystem = valueOf(raw)
    }
}

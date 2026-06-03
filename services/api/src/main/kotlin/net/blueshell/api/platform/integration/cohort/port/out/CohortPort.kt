package net.blueshell.api.platform.integration.cohort.port.out

import net.blueshell.api.platform.integration.sync.port.TargetSystem

/**
 * Outbound (driven) port for cohort membership sync. Each
 * implementation is bound to one [TargetSystem] and lives under
 * `cohort/adapter/<vendor>/`.
 *
 * The contract is operation-based rather than state-replace: callers
 * add or remove a single `(user, cohort)` pair by external id, and
 * the cohort's external counterpart is created lazily on first use.
 * All ids are passed as `String` so Discord snowflakes and Google
 * group emails fit alongside Brevo's numeric list ids without forcing
 * every adapter to coerce to `Long`.
 *
 * The orchestration layer picks the right implementation out of
 * `List<CohortPort>` keyed by [net.blueshell.api.platform.integration.cohort.persistence.Cohort.system].
 */
interface CohortPort {
    val system: TargetSystem

    /**
     * Creates the cohort on the external system and returns its native
     * id (the snowflake / list id / group address). The `hint` may be
     * used by the adapter as a folder/parent classification when the
     * external system supports it (e.g. Brevo folder id); adapters that
     * do not need a hint may ignore it.
     */
    fun createCohort(label: String, hint: String? = null): String

    fun addMember(externalUserId: String, externalCohortId: String)

    fun removeMember(externalUserId: String, externalCohortId: String)

    fun deleteCohort(externalCohortId: String)

    /**
     * Lists all members currently present in the external target.
     * Returns a bounded list; the largest live cohort is ~350 rows.
     * If a cohort ever grows past ~2 000, switch to cursor-based pages.
     */
    fun listMembers(externalCohortId: String): List<MemberRef>
}

/** One member as the external system knows them: a native id and an optional human-readable label. */
data class MemberRef(val externalUserId: String, val label: String?)

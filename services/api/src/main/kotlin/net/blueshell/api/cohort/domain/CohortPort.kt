package net.blueshell.api.cohort.domain

import net.blueshell.api.shared.enums.TargetSystem

/**
 * Outbound port for cohort membership sync, one implementation per [TargetSystem], chosen out
 * of `List<CohortPort>` by the cohort's system.
 *
 * Operation-based rather than state-replacing: a caller adds or removes one `(user, cohort)`
 * pair by external id, and the cohort's target must already be linked — creating one is an
 * operator's act. Ids are `String` so Discord snowflakes and Google group emails sit alongside
 * Brevo's numeric list ids without every adapter coercing to `Long`.
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

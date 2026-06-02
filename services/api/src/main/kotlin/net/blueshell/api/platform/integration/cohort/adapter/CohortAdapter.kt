package net.blueshell.api.platform.integration.cohort.adapter

import net.blueshell.api.platform.integration.sync.port.TargetSystem

/**
 * Per-system adapter that materialises a [Cohort][net.blueshell.api.platform.integration.cohort.persistence.Cohort]
 * on an external service and toggles a user's membership in it.
 *
 * Cohort sync mirrors the existing aggregate-state [SyncTarget][net.blueshell.api.platform.integration.sync.port.SyncTarget]
 * shape but the contract is operation-based rather than state-replace:
 * adapters add/remove a user from a cohort by external id, and lazily
 * create the cohort's external counterpart on first use. All ids are
 * passed as strings so Discord snowflakes and Google group emails fit
 * alongside Brevo's numeric list ids.
 *
 * Each adapter is bound to one [TargetSystem]; the orchestration layer
 * picks the right adapter from `List<CohortAdapter>` keyed by
 * [Cohort.system]. Discord and Google adapters land in later PRs.
 */
interface CohortAdapter {
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
}

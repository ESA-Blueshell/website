package net.blueshell.api.platform.integration.cohort.application

// Value types used by the CohortDrift port and CohortDriftService.
// Kept in the application package so they travel with the use-case layer.

import net.blueshell.api.platform.integration.sync.port.TargetSystem

/** Classifies one external member that is not in the desired local set. */
sealed interface ExtraRow {
    val externalUserId: String
    val label: String?

    data class KnownLocalUser(
        override val externalUserId: String,
        override val label: String?,
        val userId: Long,
        val fullName: String?,
        val email: String?,
        val softDeleted: Boolean,
    ) : ExtraRow

    data class UnknownExternal(
        override val externalUserId: String,
        override val label: String?,
    ) : ExtraRow
}

/** A locally-desired member that is absent from the external target. */
data class MissingRow(val userId: Long, val hasExternalMapping: Boolean)

/** The full drift picture for one (subject, system) pair. */
data class DriftReport(
    val cohortId: Long,
    val system: TargetSystem,
    val externalCohortId: String?,
    val extras: List<ExtraRow>,
    val missing: List<MissingRow>,
) {
    companion object {
        fun notMaterialised(cohortId: Long, system: TargetSystem) =
            DriftReport(cohortId, system, null, emptyList(), emptyList())
    }
}

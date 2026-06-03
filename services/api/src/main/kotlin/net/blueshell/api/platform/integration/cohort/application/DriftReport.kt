package net.blueshell.api.platform.integration.cohort.application

// Value types used by the CohortDrift port and CohortDriftService.
// Kept in the application package so they travel with the use-case layer.

import net.blueshell.api.platform.integration.sync.port.TargetSystem
import java.time.Instant

enum class DriftExtraKind { KNOWN_LOCAL_USER, UNKNOWN_EXTERNAL }

/** Classifies one external member that is not in the desired local set. */
data class ExtraRow(
    val externalUserId: String,
    val label: String?,
    val kind: DriftExtraKind,
    val userId: Long? = null,
    val fullName: String? = null,
    val email: String? = null,
    val softDeleted: Boolean? = null,
)

/** A locally-desired member that is absent from the observed external ledger. */
data class MissingRow(val userId: Long, val hasExternalMapping: Boolean)

/** The full drift picture for one (subject, system) pair, backed by the membership ledger. */
data class DriftReport(
    val cohortId: Long,
    val system: TargetSystem,
    val externalCohortId: String?,
    val extras: List<ExtraRow>,
    val missing: List<MissingRow>,
    /** Timestamp of the most recent reconcile run; null if never reconciled. */
    val lastReconciledAt: Instant?,
) {
    companion object {
        fun notMaterialised(cohortId: Long, system: TargetSystem) =
            DriftReport(cohortId, system, null, emptyList(), emptyList(), null)
    }
}

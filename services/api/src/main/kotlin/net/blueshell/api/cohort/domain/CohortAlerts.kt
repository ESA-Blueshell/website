package net.blueshell.api.cohort.domain

import net.blueshell.api.alerts.api.AlertAudience
import net.blueshell.api.alerts.api.AlertKind
import net.blueshell.api.alerts.api.AlertSource
import net.blueshell.api.alerts.api.RaisedAlert
import net.blueshell.api.cohort.persistence.CohortRepository
import net.blueshell.api.cohort.persistence.TargetReconcileRunRepository
import net.blueshell.api.cohort.persistence.TargetRepository
import net.blueshell.api.shared.enums.TargetSystem
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/**
 * For the board: a cohort that should have a Brevo list and has none, and a target whose latest
 * reconcile found people on one side only. The subject of either is the cohort, whose page deals
 * with both; a drifting target without a cohort has none.
 */
@Component
class CohortAlerts(
    private val cohorts: CohortRepository,
    private val targets: TargetRepository,
    private val runs: TargetReconcileRunRepository,
) : AlertSource {
    override val audience = AlertAudience.BOARD

    @Transactional(readOnly = true)
    override fun raised(): List<RaisedAlert> = withoutList() + drifting()

    private fun withoutList(): List<RaisedAlert> =
        cohorts
            .findAll()
            .filter { it.type.listedOnBrevo }
            .filter { targets.findByCohortIdAndSystem(requireNotNull(it.id), TargetSystem.BREVO.name)?.externalId == null }
            .map { cohort ->
                RaisedAlert(
                    key = "cohort-without-list:${cohort.id}",
                    kind = AlertKind.COHORT_WITHOUT_LIST,
                    subjectId = cohort.id,
                    subjectLabel = cohort.label,
                    count = 1,
                    since = null,
                )
            }

    private fun drifting(): List<RaisedAlert> =
        targets.findAll().filter { it.externalId != null }.mapNotNull { target ->
            val run = runs.findFirstByTargetIdOrderByStartedAtDesc(requireNotNull(target.id))
            val drift = run?.let { it.oursOnly + it.theirsOnly } ?: 0
            if (run == null || drift == 0) return@mapNotNull null
            RaisedAlert(
                key = "target-drift:${target.id}",
                kind = AlertKind.TARGET_DRIFT,
                subjectId = target.cohortId,
                subjectLabel = target.label,
                count = drift.toLong(),
                since = run.startedAt,
            )
        }
}

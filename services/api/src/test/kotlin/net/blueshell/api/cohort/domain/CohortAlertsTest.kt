package net.blueshell.api.cohort.domain

import net.blueshell.api.alerts.api.AlertAudience
import net.blueshell.api.alerts.api.AlertKind
import net.blueshell.api.alerts.api.RaisedAlert
import net.blueshell.api.cohort.persistence.CohortRepository
import net.blueshell.api.cohort.persistence.CohortType
import net.blueshell.api.cohort.persistence.TargetCount
import net.blueshell.api.cohort.persistence.TargetMemberRepository
import net.blueshell.api.cohort.persistence.TargetReconcileRun
import net.blueshell.api.cohort.persistence.TargetReconcileRunRepository
import net.blueshell.api.cohort.persistence.TargetRepository
import net.blueshell.api.testsupport.Entities
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.time.Instant

class CohortAlertsTest {
    private val cohorts: CohortRepository = mock()
    private val targets: TargetRepository = mock()
    private val runs: TargetReconcileRunRepository = mock()
    private val members: TargetMemberRepository = mock()
    private val alerts = CohortAlerts(cohorts, targets, runs, LedgerDrift(members))

    private fun counted(
        targetId: Long,
        people: Long,
    ) = object : TargetCount {
        override val targetId = targetId
        override val people = people
    }

    private val at = Instant.parse("2026-09-30T03:00:00Z")

    @Test
    fun `a cohort that should have a Brevo list and has none raises a board alert`() {
        whenever(cohorts.findAll()).thenReturn(
            listOf(
                Entities.cohort(1, label = "Paid 2026"),
                Entities.cohort(2),
                Entities.cohort(3),
                Entities.cohort(4, type = CohortType.ACTIVISTS),
            ),
        )
        whenever(targets.findByCohortIdAndSystem(2, "BREVO")).thenReturn(Entities.target(20, cohortId = 2, externalId = "7"))
        whenever(targets.findByCohortIdAndSystem(3, "BREVO")).thenReturn(Entities.target(30, cohortId = 3))

        assertThat(alerts.audience).isEqualTo(AlertAudience.BOARD)
        assertThat(alerts.raised().map { it.key }).containsExactly("cohort-without-list:1", "cohort-without-list:3")
        assertThat(alerts.raised().first())
            .isEqualTo(RaisedAlert("cohort-without-list:1", AlertKind.COHORT_WITHOUT_LIST, 1, "Paid 2026", 1, null))
    }

    @Test
    fun `a reconciled target whose ledger has drift raises a board alert on its cohort`() {
        whenever(targets.findAll()).thenReturn(
            listOf(
                Entities.target(20, label = "Sitecie", cohortId = 2, externalId = "7"),
                Entities.target(21, externalId = "8"),
                Entities.target(22, externalId = "9"),
                Entities.target(23),
            ),
        )
        whenever(runs.findFirstByTargetIdOrderByStartedAtDesc(20)).thenReturn(TargetReconcileRun(20, at, null, 10, 2, 1))
        // The run on 21 found two people missing, who were pushed since; 22 was never reconciled.
        whenever(runs.findFirstByTargetIdOrderByStartedAtDesc(21)).thenReturn(TargetReconcileRun(21, at, null, 10, 2, 0))
        whenever(members.countDesiredByTarget()).thenReturn(listOf(counted(20, 2), counted(22, 5)))
        whenever(members.countStrangersByTarget()).thenReturn(listOf(counted(20, 1)))

        assertThat(alerts.raised()).containsExactly(RaisedAlert("target-drift:20", AlertKind.TARGET_DRIFT, 2, "Sitecie", 3, at))
    }
}

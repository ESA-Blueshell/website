package net.blueshell.api.jobs.domain

import net.blueshell.api.alerts.api.AlertAudience
import net.blueshell.api.alerts.api.AlertKind
import net.blueshell.api.alerts.api.RaisedAlert
import net.blueshell.api.jobs.persistence.JobExecutionRepository
import net.blueshell.api.jobs.persistence.JobMoment
import net.blueshell.api.shared.enums.JobExecutionStatus
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.time.Instant

class JobAlertsTest {
    private val executions: JobExecutionRepository = mock()
    private val alerts = JobAlerts(executions)

    private fun moment(
        finishedAt: Instant?,
        updatedAt: Instant = Instant.EPOCH,
    ) = object : JobMoment {
        override val finishedAt = finishedAt
        override val updatedAt = updatedAt
    }

    @Test
    fun `dead jobs raise one admin alert, since the oldest died and marked by the newest`() {
        val at = Instant.parse("2026-09-30T10:00:00Z")
        whenever(executions.newestIdWithStatus(JobExecutionStatus.DEAD)).thenReturn(12)
        whenever(executions.findTopByStatusOrderByIdAsc(JobExecutionStatus.DEAD)).thenReturn(moment(finishedAt = at))
        whenever(executions.countByStatus(JobExecutionStatus.DEAD)).thenReturn(3)

        assertThat(alerts.audience).isEqualTo(AlertAudience.ADMIN)
        assertThat(alerts.raised()).containsExactly(RaisedAlert("job-dead", AlertKind.JOB_DEAD, null, null, 3, at, mark = 12))
    }

    @Test
    fun `a dead job that never finished is dated by its last change`() {
        val at = Instant.parse("2026-09-30T11:00:00Z")
        whenever(executions.newestIdWithStatus(JobExecutionStatus.DEAD)).thenReturn(4)
        whenever(executions.findTopByStatusOrderByIdAsc(JobExecutionStatus.DEAD)).thenReturn(moment(finishedAt = null, updatedAt = at))

        assertThat(alerts.raised().single().since).isEqualTo(at)
    }

    @Test
    fun `no dead job raises nothing`() {
        whenever(executions.newestIdWithStatus(JobExecutionStatus.DEAD)).thenReturn(null)

        assertThat(alerts.raised()).isEmpty()
    }
}

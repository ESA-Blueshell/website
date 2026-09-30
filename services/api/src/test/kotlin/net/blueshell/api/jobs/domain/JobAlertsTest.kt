package net.blueshell.api.jobs.domain

import net.blueshell.api.alerts.api.AlertAudience
import net.blueshell.api.alerts.api.AlertKind
import net.blueshell.api.alerts.api.RaisedAlert
import net.blueshell.api.jobs.persistence.JobExecution
import net.blueshell.api.jobs.persistence.JobExecutionRepository
import net.blueshell.api.shared.enums.JobExecutionStatus
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.time.Instant

class JobAlertsTest {
    private val executions: JobExecutionRepository = mock()
    private val alerts = JobAlerts(executions)

    @Test
    fun `dead jobs raise one admin alert, keyed by the latest so a new one shows again`() {
        val at = Instant.parse("2026-09-30T10:00:00Z")
        val latest = JobExecution(jobType = "contact.sync", payload = "{}").apply { finishedAt = at }
        latest.id = 12
        whenever(executions.findTopByStatusOrderByIdDesc(JobExecutionStatus.DEAD)).thenReturn(latest)
        whenever(executions.countByStatus(JobExecutionStatus.DEAD)).thenReturn(3)

        assertThat(alerts.audience).isEqualTo(AlertAudience.ADMIN)
        assertThat(alerts.raised()).containsExactly(RaisedAlert("job-dead:12", AlertKind.JOB_DEAD, null, null, 3, at))
    }

    @Test
    fun `no dead job raises nothing`() {
        assertThat(alerts.raised()).isEmpty()
    }
}

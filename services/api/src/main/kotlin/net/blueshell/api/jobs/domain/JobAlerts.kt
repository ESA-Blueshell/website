package net.blueshell.api.jobs.domain

import net.blueshell.api.alerts.api.AlertAudience
import net.blueshell.api.alerts.api.AlertKind
import net.blueshell.api.alerts.api.AlertSource
import net.blueshell.api.alerts.api.RaisedAlert
import net.blueshell.api.jobs.persistence.JobExecutionRepository
import net.blueshell.api.shared.enums.JobExecutionStatus
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/** Dead jobs, for an admin to retry; the alert clears once none is left dead. */
@Component
class JobAlerts(
    private val executions: JobExecutionRepository,
) : AlertSource {
    override val audience = AlertAudience.ADMIN

    @Transactional(readOnly = true)
    override fun raised(): List<RaisedAlert> {
        val latest = executions.findTopByStatusOrderByIdDesc(JobExecutionStatus.DEAD) ?: return emptyList()
        return listOf(
            RaisedAlert(
                key = "job-dead:${latest.id}",
                kind = AlertKind.JOB_DEAD,
                subjectId = null,
                subjectLabel = null,
                count = executions.countByStatus(JobExecutionStatus.DEAD),
                since = latest.finishedAt ?: latest.updatedAt,
            ),
        )
    }
}

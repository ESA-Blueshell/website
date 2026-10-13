package net.blueshell.api.jobs.domain

import net.blueshell.api.alerts.api.AlertAudience
import net.blueshell.api.alerts.api.AlertKind
import net.blueshell.api.alerts.api.AlertSource
import net.blueshell.api.alerts.api.RaisedAlert
import net.blueshell.api.jobs.persistence.JobExecutionRepository
import net.blueshell.api.shared.enums.JobExecutionStatus
import org.springframework.stereotype.Component

/** Dead jobs, for an admin to retry; the alert clears once none is left dead. */
@Component
class JobAlerts(
    private val executions: JobExecutionRepository,
) : AlertSource {
    override val audience = AlertAudience.ADMIN

    override fun raised(): List<RaisedAlert> {
        val newest = executions.newestIdWithStatus(JobExecutionStatus.DEAD) ?: return emptyList()
        val oldest = executions.findTopByStatusOrderByIdAsc(JobExecutionStatus.DEAD)
        return listOf(
            RaisedAlert(
                key = "job-dead",
                kind = AlertKind.JOB_DEAD,
                subjectId = null,
                subjectLabel = null,
                count = executions.countByStatus(JobExecutionStatus.DEAD),
                since = oldest?.finishedAt ?: oldest?.updatedAt,
                mark = newest,
            ),
        )
    }
}

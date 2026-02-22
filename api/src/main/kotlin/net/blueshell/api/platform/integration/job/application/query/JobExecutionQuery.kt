package net.blueshell.api.platform.integration.job.application.query

import net.blueshell.api.shared.enums.ActionActorType
import net.blueshell.api.shared.enums.JobExecutionStatus

data class JobExecutionQuery(
    var status: JobExecutionStatus? = null,
    var category: String? = null,
    var search: String? = null,
    var initiatedByType: ActionActorType? = null,
    var jobType: String? = null
)

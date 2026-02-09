package net.blueshell.api.platform.integration.job.dto

import net.blueshell.api.platform.integration.job.model.JobExecutionStatus
import java.time.Instant

data class JobExecutionDTO(
    val id: Long,
    val jobType: String,
    val status: JobExecutionStatus,
    val payload: String?,
    val errorMessage: String?,
    val attempts: Int,
    val queuedAt: Instant?,
    val startedAt: Instant?,
    val finishedAt: Instant?,
    val createdAt: Instant?,
    val updatedAt: Instant?
)

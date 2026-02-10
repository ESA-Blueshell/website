package net.blueshell.api.platform.integration.job.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import net.blueshell.api.shared.enums.JobExecutionStatus
import java.time.Instant

data class JobExecutionDTO(
    val id: Long?,

    @field:NotBlank
    val jobType: String?,

    @field:NotNull
    val status: JobExecutionStatus?,

    val payload: String?,
    val errorMessage: String?,

    @field:NotNull
    val attempts: Int?,

    val queuedAt: Instant?,
    val startedAt: Instant?,
    val finishedAt: Instant?,
    val createdAt: Instant?,
    val updatedAt: Instant?
)

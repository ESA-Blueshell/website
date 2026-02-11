package net.blueshell.api.platform.integration.job.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import net.blueshell.api.shared.enums.JobExecutionStatus
import java.time.Instant

@Schema(name = "JobExecution")
data class JobExecutionDTO(
    val id: Long?,

    @field:NotBlank
    var jobType: String?,

    @field:NotNull
    var status: JobExecutionStatus?,

    val payload: String?,
    val errorMessage: String?,

    @field:NotNull
    var attempts: Int?,

    val queuedAt: Instant?,
    val startedAt: Instant?,
    val finishedAt: Instant?,
    val createdAt: Instant?,
    val updatedAt: Instant?
)

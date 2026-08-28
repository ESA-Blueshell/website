package net.blueshell.api.jobs.web

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import net.blueshell.api.shared.enums.ActionActorType
import net.blueshell.api.shared.enums.ContactSystem
import net.blueshell.api.shared.enums.JobExecutionCategory
import net.blueshell.api.shared.enums.JobExecutionStatus
import net.blueshell.api.shared.enums.Role
import net.blueshell.api.shared.tracking.Actor
import java.time.Instant

@Schema(name = "JobExecution")
data class JobExecutionDTO(
    val id: Long?,

    @field:NotBlank
    var jobType: String?,
    val category: JobExecutionCategory?,
    val targetSystem: ContactSystem?,

    @field:NotNull
    var status: JobExecutionStatus?,

    val errorMessage: String?,
    val errorType: String?,
    val errorReason: String?,
    val stackTrace: String?,

    @field:NotNull
    var attempts: Int?,

    val dedupKey: String?,

    /**
     * The raw job payload parsed into a key/value map (or `null` when the
     * stored payload was empty or unparseable). Shipped as structured data
     * so the admin UI can render fields itself — no formatting applied here.
     */
    val payload: Map<String, Any?>?,

    val queuedAt: Instant?,
    val startedAt: Instant?,
    val finishedAt: Instant?,
    val nextAttemptAt: Instant?,
    val actor: Actor?,
    val initiatedByUserId: Long?,
    val initiatedByType: ActionActorType?,
    val initiatedByRole: Role?,
    val initiatedByDisplay: String?,
    val initiatedByUsername: String?,
    val initiatedByFullName: String?,
    val relatedEntities: List<JobExecutionRelatedEntityDTO>,
    val createdAt: Instant?,
    val updatedAt: Instant?
)

@Schema(name = "JobExecutionRelatedEntity")
data class JobExecutionRelatedEntityDTO(
    val type: String,
    val id: Long?,
    val label: String
)

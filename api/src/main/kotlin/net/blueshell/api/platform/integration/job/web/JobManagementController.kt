package net.blueshell.api.platform.integration.job.web

import io.swagger.v3.oas.annotations.tags.Tag
import net.blueshell.api.platform.integration.job.dto.JobExecutionDTO
import net.blueshell.api.platform.integration.job.model.JobExecution
import net.blueshell.api.platform.integration.job.service.JobExecutionService
import net.blueshell.api.platform.integration.queue.JobDispatcher
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/management/jobs")
@Tag(name = "Job Management", description = "API for managing job executions")
class JobManagementController(
    private val jobExecutionService: JobExecutionService,
    private val jobDispatcher: JobDispatcher
) {
    @GetMapping
    @PreAuthorize("hasPermission(null, 'JobExecution', 'read')")
    fun list(): List<JobExecutionDTO> {
        return jobExecutionService.findRecent().map { it.toDto() }
    }

    @PostMapping("/{id}/retry")
    @PreAuthorize("hasPermission(null, 'JobExecution', 'retry')")
    fun retry(@PathVariable id: Long): JobExecutionDTO {
        val execution = jobExecutionService.findById(id)
        val requeued = jobExecutionService.requeue(execution)
        jobDispatcher.requeue(requeued)
        return requeued.toDto()
    }

    private fun JobExecution.toDto(): JobExecutionDTO = JobExecutionDTO(
        id = id,
        jobType = jobType,
        status = status,
        payload = payload,
        errorMessage = errorMessage,
        errorType = errorType,
        errorReason = errorReason,
        attempts = attempts,
        queuedAt = queuedAt,
        startedAt = startedAt,
        finishedAt = finishedAt,
        actor = actor,
        initiatedByUserId = initiatedByUserId,
        initiatedByType = initiatedByType,
        initiatedByRole = initiatedByRole,
        createdAt = createdAt,
        updatedAt = updatedAt
    )
}

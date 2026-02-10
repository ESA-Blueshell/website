package net.blueshell.api.platform.integration.job.web

import net.blueshell.api.platform.integration.job.dto.JobExecutionDTO
import net.blueshell.api.platform.integration.job.model.JobExecution
import net.blueshell.api.platform.integration.queue.JobDispatcher
import net.blueshell.api.platform.integration.job.service.JobExecutionService
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ResponseStatusException

@RestController
@RequestMapping("/management/jobs")
@PreAuthorize("hasAuthority('ADMIN')")
class JobManagementController(
    private val jobExecutionService: JobExecutionService,
    private val jobDispatcher: JobDispatcher
) {
    @GetMapping
    fun list(): List<JobExecutionDTO> {
        return jobExecutionService.findRecent().map { it.toDto() }
    }

    @PostMapping("/{id}/retry")
    fun retry(@PathVariable id: Long): JobExecutionDTO {
        val execution = jobExecutionService.findById(id)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Job execution not found.")
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
        attempts = attempts,
        queuedAt = queuedAt,
        startedAt = startedAt,
        finishedAt = finishedAt,
        createdAt = createdAt,
        updatedAt = updatedAt
    )
}

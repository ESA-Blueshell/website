package net.blueshell.api.controller

import net.blueshell.api.dto.job.JobExecutionDTO
import net.blueshell.api.model.job.JobExecution
import net.blueshell.api.queue.JobDispatcher
import net.blueshell.api.service.job.JobExecutionService
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
        id = id ?: 0L,
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

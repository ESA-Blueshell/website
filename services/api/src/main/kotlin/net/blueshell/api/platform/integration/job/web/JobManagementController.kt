package net.blueshell.api.platform.integration.job.web

import io.micrometer.core.instrument.MeterRegistry
import io.swagger.v3.oas.annotations.tags.Tag
import net.blueshell.api.platform.integration.job.application.query.JobExecutionQuery
import net.blueshell.api.platform.integration.job.application.service.JobExecutionService
import net.blueshell.api.platform.integration.job.web.service.JobExecutionViewService
import net.blueshell.api.platform.integration.job.web.dto.JobExecutionDTO
import net.blueshell.api.platform.integration.job.web.dto.JobStatsDTO
import net.blueshell.api.platform.integration.queue.JobExecutor
import net.blueshell.api.shared.enums.JobExecutionStatus
import org.springdoc.core.annotations.ParameterObject
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.data.web.PageableDefault
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ResponseStatusException
import java.util.concurrent.TimeUnit

@RestController
@RequestMapping("/management/jobs")
@Tag(name = "Job Management", description = "API for managing job executions")
class JobManagementController(
    private val jobExecutionService: JobExecutionService,
    private val jobExecutor: JobExecutor,
    private val views: JobExecutionViewService,
    private val meterRegistry: MeterRegistry
) {
    @GetMapping
    @PreAuthorize("hasPermission('__NO_TARGET__', 'JobExecution', 'read')")
    fun list(
        @ParameterObject
        @PageableDefault(size = PAGE_SIZE, sort = ["updatedAt"], direction = Sort.Direction.DESC)
        pageable: Pageable,
        @ParameterObject filter: JobExecutionQuery = JobExecutionQuery()
    ): Page<JobExecutionDTO> {
        val page = jobExecutionService.findByFilter(normalizePageable(pageable), filter)
        val content = views.toDtos(page.content)
        return PageImpl(content, page.pageable, page.totalElements)
    }

    @PostMapping("/{id}/retry")
    @PreAuthorize("hasPermission('__NO_TARGET__', 'JobExecution', 'retry')")
    fun retry(@PathVariable id: Long): JobExecutionDTO {
        val execution = jobExecutionService.findById(id)
        if (execution.status != JobExecutionStatus.FAILED && execution.status != JobExecutionStatus.DEAD) {
            throw ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Only FAILED or DEAD jobs can be retried. Current status: ${execution.status}"
            )
        }
        val requeued = jobExecutionService.retryWithSupersede(execution)
        jobExecutor.executeAsync(requeued.id!!)
        return views.toDto(requeued)
    }

    @GetMapping("/stats")
    @PreAuthorize("hasAnyAuthority('BOARD', 'ADMIN')")
    fun getStats(): JobStatsDTO {
        val countByStatus = jobExecutionService.countAllByStatus()

        val deadSinceStartup = meterRegistry.find("job.dead.count").counters()
            .sumOf { it.count() }
        val failedSinceStartup = meterRegistry.find("job.failed.count").counters()
            .sumOf { it.count() }
        val recoveriesSinceStartup = meterRegistry.find("job.recovery.count")
            .counter()?.count() ?: 0.0
        val avgSuccessDuration = meterRegistry.find("job.execution.duration")
            .tag("outcome", "success").timers()
            .filter { it.count() > 0 }
            .map { it.mean(TimeUnit.SECONDS) }
            .takeIf { it.isNotEmpty() }?.average() ?: 0.0

        return JobStatsDTO(
            totalCount = countByStatus.values.sum(),
            successCount = countByStatus[JobExecutionStatus.SUCCESS] ?: 0L,
            failedCount = countByStatus[JobExecutionStatus.FAILED] ?: 0L,
            deadCount = countByStatus[JobExecutionStatus.DEAD] ?: 0L,
            queuedCount = countByStatus[JobExecutionStatus.QUEUED] ?: 0L,
            runningCount = countByStatus[JobExecutionStatus.RUNNING] ?: 0L,
            deadSinceStartup = deadSinceStartup,
            failedSinceStartup = failedSinceStartup,
            recoveriesSinceStartup = recoveriesSinceStartup,
            avgSuccessDurationSeconds = avgSuccessDuration,
        )
    }

    private fun normalizePageable(pageable: Pageable): Pageable {
        val sort = if (pageable.sort.isSorted) pageable.sort else DEFAULT_SORT
        val pageNumber = if (pageable.isPaged) pageable.pageNumber else 0
        return PageRequest.of(pageNumber, PAGE_SIZE, sort)
    }

    companion object {
        private const val PAGE_SIZE = 50
        private val DEFAULT_SORT: Sort = Sort.by(
            Sort.Order.desc("updatedAt"),
            Sort.Order.desc("id")
        )
    }
}

package net.blueshell.api.platform.integration.job.web

import io.swagger.v3.oas.annotations.tags.Tag
import net.blueshell.api.platform.integration.job.application.query.JobExecutionQuery
import net.blueshell.api.platform.integration.job.dto.JobExecutionDTO
import net.blueshell.api.platform.integration.job.service.JobExecutionService
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

@RestController
@RequestMapping("/management/jobs")
@Tag(name = "Job Management", description = "API for managing job executions")
class JobManagementController(
    private val jobExecutionService: JobExecutionService,
    private val jobExecutor: JobExecutor,
    private val views: JobExecutionViewService
) {
    @GetMapping
    @PreAuthorize("hasPermission('__NO_TARGET__', 'JobExecution', 'read')")
    fun list(
        @ParameterObject
        @PageableDefault(size = PAGE_SIZE, sort = ["createdAt"], direction = Sort.Direction.DESC)
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
        val requeued = jobExecutionService.requeue(execution)
        jobExecutor.executeAsync(requeued.id!!)
        return views.toDto(requeued)
    }

    private fun normalizePageable(pageable: Pageable): Pageable {
        val sort = if (pageable.sort.isSorted) pageable.sort else DEFAULT_SORT
        val pageNumber = if (pageable.isPaged) pageable.pageNumber else 0
        return PageRequest.of(pageNumber, PAGE_SIZE, sort)
    }

    companion object {
        private const val PAGE_SIZE = 50
        private val DEFAULT_SORT: Sort = Sort.by(
            Sort.Order.desc("createdAt"),
            Sort.Order.desc("id")
        )
    }
}

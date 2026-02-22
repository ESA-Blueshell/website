package net.blueshell.api.platform.integration.job.web

import io.swagger.v3.oas.annotations.tags.Tag
import net.blueshell.api.platform.integration.job.application.query.JobExecutionQuery
import net.blueshell.api.platform.integration.job.dto.JobExecutionDTO
import net.blueshell.api.platform.integration.job.service.JobExecutionService
import net.blueshell.api.platform.integration.queue.JobDispatcher
import org.springdoc.core.annotations.ParameterObject
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.data.web.PageableDefault
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/management/jobs")
@Tag(name = "Job Management", description = "API for managing job executions")
class JobManagementController(
    private val jobExecutionService: JobExecutionService,
    private val jobDispatcher: JobDispatcher,
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
        val requeued = jobExecutionService.requeue(execution)
        jobDispatcher.requeue(requeued)
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

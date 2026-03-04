package net.blueshell.api.platform.integration.email.web

import io.swagger.v3.oas.annotations.tags.Tag
import net.blueshell.api.platform.integration.email.application.query.EmailOutboxQuery
import net.blueshell.api.platform.integration.email.application.service.EmailOutboxService
import net.blueshell.api.platform.integration.email.dto.EmailOutboxDTO
import net.blueshell.api.platform.integration.email.dto.EmailOutboxStatsDTO
import net.blueshell.api.platform.integration.email.persistence.EmailOutbox
import net.blueshell.api.platform.integration.job.service.JobExecutionService
import net.blueshell.api.platform.integration.queue.JobExecutor
import net.blueshell.api.shared.enums.EmailDeliveryStatus
import net.blueshell.api.shared.enums.JobExecutionStatus
import org.springdoc.core.annotations.ParameterObject
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.data.web.PageableDefault
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ResponseStatusException

@RestController
@RequestMapping("/management/emails")
@Tag(name = "Email Management", description = "API for managing the email outbox")
class EmailManagementController(
    private val emailOutboxService: EmailOutboxService,
    private val jobExecutionService: JobExecutionService,
    private val jobExecutor: JobExecutor,
) {
    @GetMapping
    @PreAuthorize("hasPermission('__NO_TARGET__', 'EmailOutbox', 'read')")
    fun list(
        @ParameterObject
        @PageableDefault(size = PAGE_SIZE, sort = ["createdAt"], direction = Sort.Direction.DESC)
        pageable: Pageable,
        @ParameterObject filter: EmailOutboxQuery = EmailOutboxQuery(),
    ): Page<EmailOutboxDTO> {
        val page = emailOutboxService.findByFilter(normalizePageable(pageable), filter)
        return page.map { it.toDto() }
    }

    @GetMapping("/stats")
    @PreAuthorize("hasAnyAuthority('BOARD', 'ADMIN')")
    fun getStats(): EmailOutboxStatsDTO {
        return EmailOutboxStatsDTO(
            totalCount = EmailDeliveryStatus.entries.sumOf { emailOutboxService.countByStatus(it) },
            pendingCount = emailOutboxService.countByStatus(EmailDeliveryStatus.PENDING),
            sentCount = emailOutboxService.countByStatus(EmailDeliveryStatus.SENT),
            deliveredCount = emailOutboxService.countByStatus(EmailDeliveryStatus.DELIVERED),
            openedCount = emailOutboxService.countByStatus(EmailDeliveryStatus.OPENED),
            bouncedCount = emailOutboxService.countByStatus(EmailDeliveryStatus.BOUNCED),
            failedCount = emailOutboxService.countByStatus(EmailDeliveryStatus.FAILED),
        )
    }

    @PostMapping("/{id}/retry")
    @PreAuthorize("hasPermission('__NO_TARGET__', 'EmailOutbox', 'retry')")
    fun retry(@PathVariable id: Long): EmailOutboxDTO {
        val outbox = emailOutboxService.findById(id)
        val jobExecutionId = outbox.jobExecutionId
            ?: throw ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Email outbox entry $id has no linked job and cannot be retried"
            )
        val jobExecution = jobExecutionService.findById(jobExecutionId)
        if (jobExecution.status != JobExecutionStatus.FAILED && jobExecution.status != JobExecutionStatus.DEAD) {
            throw ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Linked job is not FAILED or DEAD (status: ${jobExecution.status}). Cannot retry."
            )
        }
        val requeued = jobExecutionService.requeue(jobExecution)
        jobExecutor.executeAsync(requeued.id!!)
        return outbox.toDto()
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
            Sort.Order.desc("id"),
        )
    }
}

private fun EmailOutbox.toDto() = EmailOutboxDTO(
    id = this.id,
    recipientEmail = this.recipientEmail,
    recipientName = this.recipientName,
    subject = this.subject,
    emailType = this.emailType,
    deliveryStatus = this.deliveryStatus,
    messageId = this.messageId,
    sentAt = this.sentAt,
    deliveredAt = this.deliveredAt,
    openedAt = this.openedAt,
    errorType = this.errorType,
    errorReason = this.errorReason,
    attempts = this.attempts,
    jobExecutionId = this.jobExecutionId,
    createdAt = this.createdAt,
    updatedAt = this.updatedAt,
)

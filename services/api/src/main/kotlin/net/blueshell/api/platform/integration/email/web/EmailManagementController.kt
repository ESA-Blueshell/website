package net.blueshell.api.platform.integration.email.web

import io.swagger.v3.oas.annotations.tags.Tag
import net.blueshell.api.platform.integration.email.application.query.EmailQuery
import net.blueshell.api.platform.integration.email.application.service.EmailService
import net.blueshell.api.platform.integration.email.application.service.SentEmailPreviewService
import net.blueshell.api.platform.integration.email.web.dto.EmailDTO
import net.blueshell.api.platform.integration.email.web.dto.EmailStatsDTO
import net.blueshell.api.platform.integration.email.web.dto.SentEmailPreviewDTO
import net.blueshell.api.platform.integration.email.persistence.Email
import net.blueshell.api.platform.integration.job.application.service.JobExecutionService
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
@Tag(name = "Email Management", description = "API for managing outbound emails")
class EmailManagementController(
    private val emailService: EmailService,
    private val sentEmailPreviewService: SentEmailPreviewService,
    private val jobExecutionService: JobExecutionService,
    private val jobExecutor: JobExecutor,
) {
    @GetMapping
    @PreAuthorize("hasPermission('__NO_TARGET__', 'Email', 'read')")
    fun list(
        @ParameterObject
        @PageableDefault(size = PAGE_SIZE, sort = ["createdAt"], direction = Sort.Direction.DESC)
        pageable: Pageable,
        @ParameterObject filter: EmailQuery = EmailQuery(),
    ): Page<EmailDTO> {
        val page = emailService.findByFilter(normalizePageable(pageable), filter)
        return page.map { it.toDto() }
    }

    @GetMapping("/stats")
    @PreAuthorize("hasAnyAuthority('BOARD', 'ADMIN')")
    fun getStats(): EmailStatsDTO {
        return EmailStatsDTO(
            totalCount = EmailDeliveryStatus.entries.sumOf { emailService.countByStatus(it) },
            pendingCount = emailService.countByStatus(EmailDeliveryStatus.PENDING),
            sentCount = emailService.countByStatus(EmailDeliveryStatus.SENT),
            deliveredCount = emailService.countByStatus(EmailDeliveryStatus.DELIVERED),
            openedCount = emailService.countByStatus(EmailDeliveryStatus.OPENED),
            bouncedCount = emailService.countByStatus(EmailDeliveryStatus.BOUNCED),
            failedCount = emailService.countByStatus(EmailDeliveryStatus.FAILED),
        )
    }

    /**
     * Renders a sent email so it can be read back, with every url stripped out of it first.
     *
     * Gated on the same permission as reading the outbox: the body carries the recipient's
     * name and whatever the email told them. What it no longer carries is any link — a sent
     * email's links are live credentials, and the redaction happens before the response
     * leaves here rather than in the browser.
     */
    @GetMapping("/{id}/preview")
    @PreAuthorize("hasPermission('__NO_TARGET__', 'Email', 'read')")
    fun previewSentEmail(@PathVariable id: Long): SentEmailPreviewDTO {
        val preview = sentEmailPreviewService.preview(id)
            ?: throw ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "Email $id was sent before its body was stored, so it cannot be previewed",
            )
        return SentEmailPreviewDTO(
            subject = preview.subject,
            html = preview.html,
            recipientEmail = preview.recipientEmail,
            recipientName = preview.recipientName,
        )
    }

    @PostMapping("/{id}/retry")
    @PreAuthorize("hasPermission('__NO_TARGET__', 'Email', 'retry')")
    fun retry(@PathVariable id: Long): EmailDTO {
        val email = emailService.findById(id)
        val jobExecutionId = email.jobExecutionId
            ?: throw ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Email $id has no linked job and cannot be retried"
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
        return email.toDto()
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

private fun Email.toDto() = EmailDTO(
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
    previewable = this.bodyMarkdown != null,
)

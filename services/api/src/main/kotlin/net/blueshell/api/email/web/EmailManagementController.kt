package net.blueshell.api.email.web

import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import net.blueshell.api.email.api.EmailPreviewRenderer
import net.blueshell.api.email.api.SiteMarkdownEmails
import net.blueshell.api.email.domain.EmailQuery
import net.blueshell.api.email.domain.EmailService
import net.blueshell.api.email.domain.SentEmailPreviewService
import net.blueshell.api.email.persistence.Email
import net.blueshell.api.jobs.api.JobExecutionService
import net.blueshell.api.jobs.api.JobExecutor
import net.blueshell.api.security.BoardOnly
import net.blueshell.api.shared.email.EmailContent
import net.blueshell.api.shared.enums.EmailDeliveryStatus
import net.blueshell.api.shared.enums.JobExecutionStatus
import net.blueshell.api.shared.job.JobQueue
import net.blueshell.api.shared.job.JobTrigger
import org.springdoc.core.annotations.ParameterObject
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.data.web.PageableDefault
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException

@RestController
@RequestMapping("/management/emails")
@Tag(name = "Email Management", description = "API for managing outbound emails")
class EmailManagementController(
    private val emailService: EmailService,
    private val sentEmailPreviewService: SentEmailPreviewService,
    private val jobExecutionService: JobExecutionService,
    private val jobExecutor: JobExecutor,
    private val jobs: JobQueue,
    private val siteMarkdown: SiteMarkdownEmails,
    private val renderer: EmailPreviewRenderer,
) {
    @GetMapping
    @BoardOnly
    fun list(
        @ParameterObject
        @PageableDefault(size = PAGE_SIZE, sort = ["createdAt"], direction = Sort.Direction.DESC)
        pageable: Pageable,
        @ParameterObject filter: EmailQuery = EmailQuery(),
    ): Page<EmailDTO> {
        val page = emailService.findByFilter(normalizePageable(pageable), filter)
        return page.map { it.toDto() }
    }

    // Counts over the same rows the listing shows, so they answer to the same permission
    // rather than to a second spelling of it that has to be kept in sync.
    @GetMapping("/stats")
    @BoardOnly
    fun getStats(): EmailStatsDTO =
        EmailStatsDTO(
            totalCount = EmailDeliveryStatus.entries.sumOf { emailService.countByStatus(it) },
            queuedCount = emailService.countByStatus(EmailDeliveryStatus.QUEUED),
            sentCount = emailService.countByStatus(EmailDeliveryStatus.SENT),
            deliveredCount = emailService.countByStatus(EmailDeliveryStatus.DELIVERED),
            openedCount = emailService.countByStatus(EmailDeliveryStatus.OPENED),
            bouncedCount = emailService.countByStatus(EmailDeliveryStatus.BOUNCED),
            failedCount = emailService.countByStatus(EmailDeliveryStatus.FAILED),
        )

    /** One email, with the emails made again from it. */
    @GetMapping("/{id}")
    @BoardOnly
    fun findEmail(
        @PathVariable id: Long,
    ): EmailDetailDTO = EmailDetailDTO(emailService.findById(id).toDto(), emailService.resendsOf(id).map { it.toDto() })

    /**
     * Renders a sent email so it can be read back, with every url stripped out of it first.
     *
     * Gated on the same permission as reading the outbox, since the body carries the
     * recipient's name. A sent email's links are live credentials, so they are stripped before
     * the response leaves here rather than in the browser.
     */
    @GetMapping("/{id}/preview")
    @BoardOnly
    fun previewSentEmail(
        @PathVariable id: Long,
    ): SentEmailPreviewDTO {
        val preview =
            sentEmailPreviewService.preview(id)
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

    /** A message from the site's editor as the email it becomes, rendered the way the send renders it. Sends nothing. */
    @PostMapping("/render")
    @BoardOnly
    fun render(
        @Valid @RequestBody request: RenderEmailRequest,
    ): RenderedEmailDTO {
        val content =
            EmailContent(
                recipientEmail = "",
                recipientName = request.recipientName,
                subject = request.subject,
                markdownContent = siteMarkdown.forEmail(request.message),
            )
        return renderer.render(content).let { RenderedEmailDTO(it.subject, it.html) }
    }

    @PostMapping("/{id}/retry")
    @BoardOnly
    fun retry(
        @PathVariable id: Long,
    ): EmailDTO {
        val email = emailService.findById(id)
        val jobExecutionId =
            email.jobExecutionId
                ?: throw ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Email $id has no linked job and cannot be retried",
                )
        val jobExecution = jobExecutionService.findById(jobExecutionId)
        if (jobExecution.status != JobExecutionStatus.FAILED && jobExecution.status != JobExecutionStatus.DEAD) {
            throw ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Linked job is not FAILED or DEAD (status: ${jobExecution.status}). Cannot retry.",
            )
        }
        val requeued = jobExecutionService.requeue(jobExecution)
        jobExecutor.executeAsync(requeued.id!!)
        return email.toDto()
    }

    /**
     * Makes the email again from what it is about as it stands now, such as the person's current
     * address, and sends that as a new email linked to this one. A retry sends this one again.
     */
    @PostMapping("/{id}/resend")
    @BoardOnly
    fun resend(
        @PathVariable id: Long,
    ): EmailDTO {
        val email = emailService.findById(id)
        val jobExecutionId =
            email.jobExecutionId
                ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Email $id has no linked job and cannot be resent")
        val queued =
            jobs.runAgain(jobExecutionId, JobTrigger.SITE_ACTION)
                ?: throw ResponseStatusException(HttpStatus.CONFLICT, "The same email is already queued")
        val made =
            emailService.linkResend(requireNotNull(queued.id), email)
                ?: throw ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "What email $id was about no longer exists, so it cannot be made again",
                )
        return made.toDto()
    }

    private fun normalizePageable(pageable: Pageable): Pageable {
        val sort = if (pageable.sort.isSorted) pageable.sort else DEFAULT_SORT
        val pageNumber = if (pageable.isPaged) pageable.pageNumber else 0
        return PageRequest.of(pageNumber, PAGE_SIZE, sort)
    }

    companion object {
        private const val PAGE_SIZE = 50
        private val DEFAULT_SORT: Sort =
            Sort.by(
                Sort.Order.desc("createdAt"),
                Sort.Order.desc("id"),
            )
    }
}

private fun Email.toDto() =
    EmailDTO(
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
        resentFromId = this.resentFromId,
        initiatedByUserId = this.initiatedByUserId,
    )

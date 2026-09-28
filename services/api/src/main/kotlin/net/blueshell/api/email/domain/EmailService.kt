package net.blueshell.api.email.domain

import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceContext
import net.blueshell.api.email.persistence.Email
import net.blueshell.api.email.persistence.EmailRepository
import net.blueshell.api.email.persistence.EmailSpecifications
import net.blueshell.api.shared.email.EmailContent
import net.blueshell.api.shared.enums.EmailDeliveryStatus
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.time.Instant
import java.util.UUID

@Service
class EmailService(
    private val repository: EmailRepository,
) {
    // Read back after each write, so the columns the database fills are on the answer.
    @PersistenceContext
    private lateinit var em: EntityManager

    private fun written(row: Email): Email = repository.saveAndFlush(row).also(em::refresh)

    // The existence query flushes the session first, which writes what the edit cascades before
    // the merge; merging it unwritten fails on a lazy owner.
    private fun rewritten(row: Email): Email {
        val id = row.id
        if (id == null || !repository.existsById(id)) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "Email not found with id: $id")
        }
        return written(row)
    }

    @Transactional(readOnly = true)
    fun findById(id: Long): Email =
        repository.findById(id).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "Email not found with id: $id")
        }

    @Transactional
    fun createPending(
        content: EmailContent,
        emailType: String,
        jobExecutionId: Long?,
    ): Email {
        val email =
            Email(
                recipientEmail = content.recipientEmail,
                recipientName = content.recipientName,
                subject = content.subject,
                bodyMarkdown = content.markdownContent,
                emailType = emailType,
                deliveryStatus = EmailDeliveryStatus.PENDING,
                trackingToken = UUID.randomUUID().toString(),
                jobExecutionId = jobExecutionId,
                attempts = 0,
            )
        return written(email)
    }

    @Transactional(readOnly = true)
    fun findByTrackingToken(token: String): Email? = repository.findByTrackingToken(token)

    @Transactional(readOnly = true)
    fun findByMessageId(messageId: String): Email? = repository.findByMessageId(messageId)

    @Transactional
    fun markSent(
        email: Email,
        messageId: String,
    ): Email {
        email.deliveryStatus = EmailDeliveryStatus.SENT
        email.messageId = messageId
        email.sentAt = Instant.now()
        email.attempts += 1
        email.errorType = null
        email.errorReason = null
        return rewritten(email)
    }

    @Transactional
    fun markFailed(
        email: Email,
        errorType: String,
        errorReason: String,
    ): Email {
        email.deliveryStatus = EmailDeliveryStatus.FAILED
        email.attempts += 1
        email.errorType = errorType
        email.errorReason = errorReason
        return rewritten(email)
    }

    @Transactional
    fun markDelivered(email: Email): Email {
        email.deliveryStatus = EmailDeliveryStatus.DELIVERED
        email.deliveredAt = Instant.now()
        return rewritten(email)
    }

    @Transactional
    fun markOpened(email: Email): Email {
        email.deliveryStatus = EmailDeliveryStatus.OPENED
        if (email.deliveredAt == null) email.deliveredAt = Instant.now()
        email.openedAt = Instant.now()
        return rewritten(email)
    }

    @Transactional
    fun markBounced(
        email: Email,
        reason: String,
    ): Email {
        email.deliveryStatus = EmailDeliveryStatus.BOUNCED
        email.errorReason = reason
        return rewritten(email)
    }

    @Transactional(readOnly = true)
    fun findSentForSync(limit: Int): List<Email> {
        val pageable = PageRequest.of(0, limit)
        // Sync entries sent more than 5 minutes ago to allow events to propagate
        val threshold = Instant.now().minusSeconds(300)
        return repository.findByDeliveryStatusAndSentAtBefore(EmailDeliveryStatus.SENT, threshold, pageable)
    }

    @Transactional(readOnly = true)
    fun findByFilter(
        pageable: Pageable,
        query: EmailQuery,
    ): Page<Email> {
        val spec = EmailSpecifications.fromQuery(query)
        return repository.findAll(spec, pageable)
    }

    fun countByStatus(status: EmailDeliveryStatus): Long = repository.countByDeliveryStatus(status)

    @Transactional(readOnly = true)
    fun findRecentByRecipientEmail(
        email: String,
        since: Instant,
    ): Email? = repository.findTopByRecipientEmailAndSentAtAfterOrderBySentAtDesc(email, since)
}

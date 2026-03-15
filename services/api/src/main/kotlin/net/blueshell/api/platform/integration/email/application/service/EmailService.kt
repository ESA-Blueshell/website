package net.blueshell.api.platform.integration.email.application.service

import net.blueshell.api.platform.integration.email.application.query.EmailQuery
import net.blueshell.api.platform.integration.email.persistence.Email
import net.blueshell.api.platform.integration.email.persistence.repository.EmailRepository
import net.blueshell.api.platform.integration.email.persistence.spec.EmailSpecifications
import net.blueshell.api.shared.email.EmailContent
import net.blueshell.api.shared.enums.EmailDeliveryStatus
import net.blueshell.api.shared.service.BaseModelService
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

@Service
class EmailService(
    repository: EmailRepository
) : BaseModelService<Email, Long, EmailRepository>(repository) {

    @Transactional
    fun createPending(content: EmailContent, emailType: String, jobExecutionId: Long?): Email {
        val email = Email(
            recipientEmail = content.recipientEmail,
            recipientName = content.recipientName,
            subject = content.subject,
            emailType = emailType,
            deliveryStatus = EmailDeliveryStatus.PENDING,
            trackingToken = UUID.randomUUID().toString(),
            jobExecutionId = jobExecutionId,
            attempts = 0,
        )
        return super.create(email)
    }

    @Transactional(readOnly = true)
    fun findByTrackingToken(token: String): Email? = repository.findByTrackingToken(token)

    @Transactional(readOnly = true)
    fun findByMessageId(messageId: String): Email? = repository.findByMessageId(messageId)

    @Transactional
    fun markSent(email: Email, messageId: String): Email {
        email.deliveryStatus = EmailDeliveryStatus.SENT
        email.messageId = messageId
        email.sentAt = Instant.now()
        email.attempts += 1
        email.errorType = null
        email.errorReason = null
        return super.update(email)
    }

    @Transactional
    fun markFailed(email: Email, errorType: String, errorReason: String): Email {
        email.deliveryStatus = EmailDeliveryStatus.FAILED
        email.attempts += 1
        email.errorType = errorType
        email.errorReason = errorReason
        return super.update(email)
    }

    @Transactional
    fun markDelivered(email: Email): Email {
        email.deliveryStatus = EmailDeliveryStatus.DELIVERED
        email.deliveredAt = Instant.now()
        return super.update(email)
    }

    @Transactional
    fun markOpened(email: Email): Email {
        email.deliveryStatus = EmailDeliveryStatus.OPENED
        if (email.deliveredAt == null) email.deliveredAt = Instant.now()
        email.openedAt = Instant.now()
        return super.update(email)
    }

    @Transactional
    fun markBounced(email: Email, reason: String): Email {
        email.deliveryStatus = EmailDeliveryStatus.BOUNCED
        email.errorReason = reason
        return super.update(email)
    }

    @Transactional(readOnly = true)
    fun findSentForSync(limit: Int): List<Email> {
        val pageable = PageRequest.of(0, limit)
        // Sync entries sent more than 5 minutes ago to allow events to propagate
        val threshold = Instant.now().minusSeconds(300)
        return repository.findByDeliveryStatusAndSentAtBefore(EmailDeliveryStatus.SENT, threshold, pageable)
    }

    @Transactional(readOnly = true)
    fun findByFilter(pageable: Pageable, query: EmailQuery): Page<Email> {
        val spec = EmailSpecifications.fromQuery(query)
        return repository.findAll(spec, pageable)
    }

    fun countByStatus(status: EmailDeliveryStatus): Long = repository.countByDeliveryStatus(status)

    @Transactional(readOnly = true)
    fun findRecentByRecipientEmail(email: String, since: Instant): Email? =
        repository.findTopByRecipientEmailAndSentAtAfterOrderBySentAtDesc(email, since)
}

package net.blueshell.api.platform.integration.email.application.service

import net.blueshell.api.platform.integration.email.application.query.EmailOutboxQuery
import net.blueshell.api.platform.integration.email.persistence.EmailOutbox
import net.blueshell.api.platform.integration.email.persistence.repository.EmailOutboxRepository
import net.blueshell.api.platform.integration.email.persistence.spec.EmailOutboxSpecifications
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
class EmailOutboxService(
    repository: EmailOutboxRepository
) : BaseModelService<EmailOutbox, Long, EmailOutboxRepository>(repository) {

    @Transactional
    fun createPending(content: EmailContent, emailType: String, jobExecutionId: Long?): EmailOutbox {
        val outbox = EmailOutbox(
            recipientEmail = content.recipientEmail,
            recipientName = content.recipientName,
            subject = content.subject,
            emailType = emailType,
            deliveryStatus = EmailDeliveryStatus.PENDING,
            trackingToken = UUID.randomUUID().toString(),
            jobExecutionId = jobExecutionId,
            attempts = 0,
        )
        return super.create(outbox)
    }

    @Transactional(readOnly = true)
    fun findByTrackingToken(token: String): EmailOutbox? = repository.findByTrackingToken(token)

    @Transactional
    fun markSent(outbox: EmailOutbox, messageId: String): EmailOutbox {
        outbox.deliveryStatus = EmailDeliveryStatus.SENT
        outbox.messageId = messageId
        outbox.sentAt = Instant.now()
        outbox.attempts += 1
        outbox.errorType = null
        outbox.errorReason = null
        return super.update(outbox)
    }

    @Transactional
    fun markFailed(outbox: EmailOutbox, errorType: String, errorReason: String): EmailOutbox {
        outbox.deliveryStatus = EmailDeliveryStatus.FAILED
        outbox.attempts += 1
        outbox.errorType = errorType
        outbox.errorReason = errorReason
        return super.update(outbox)
    }

    @Transactional
    fun markDelivered(outbox: EmailOutbox): EmailOutbox {
        outbox.deliveryStatus = EmailDeliveryStatus.DELIVERED
        outbox.deliveredAt = Instant.now()
        return super.update(outbox)
    }

    @Transactional
    fun markOpened(outbox: EmailOutbox): EmailOutbox {
        outbox.deliveryStatus = EmailDeliveryStatus.OPENED
        if (outbox.deliveredAt == null) outbox.deliveredAt = Instant.now()
        outbox.openedAt = Instant.now()
        return super.update(outbox)
    }

    @Transactional
    fun markBounced(outbox: EmailOutbox, reason: String): EmailOutbox {
        outbox.deliveryStatus = EmailDeliveryStatus.BOUNCED
        outbox.errorReason = reason
        return super.update(outbox)
    }

    @Transactional(readOnly = true)
    fun findSentForSync(limit: Int): List<EmailOutbox> {
        val pageable = PageRequest.of(0, limit)
        // Sync entries sent more than 5 minutes ago to allow events to propagate
        val threshold = Instant.now().minusSeconds(300)
        return repository.findByDeliveryStatusAndSentAtBefore(EmailDeliveryStatus.SENT, threshold, pageable)
    }

    @Transactional(readOnly = true)
    fun findByFilter(pageable: Pageable, query: EmailOutboxQuery): Page<EmailOutbox> {
        val spec = EmailOutboxSpecifications.fromQuery(query)
        return repository.findAll(spec, pageable)
    }

    fun countByStatus(status: EmailDeliveryStatus): Long = repository.countByDeliveryStatus(status)
}

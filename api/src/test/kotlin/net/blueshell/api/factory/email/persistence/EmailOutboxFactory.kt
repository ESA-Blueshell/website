package net.blueshell.api.factory.email.persistence

import net.blueshell.api.factory.support.FactoryPersistenceSupport
import net.blueshell.api.platform.integration.email.persistence.EmailOutbox
import net.blueshell.api.shared.enums.EmailDeliveryStatus
import org.springframework.stereotype.Component
import java.time.Instant

@Component
class EmailOutboxFactory(
    private val persistence: FactoryPersistenceSupport,
) {
    fun build(
        recipientEmail: String = "recipient@example.com",
        recipientName: String = "Test Recipient",
        subject: String = "Test Subject",
        emailType: String = "email.test",
        deliveryStatus: EmailDeliveryStatus = EmailDeliveryStatus.SENT,
        messageId: String? = "<test-msg-${System.nanoTime()}@test.blueshell.net>",
        sentAt: Instant? = Instant.now(),
        jobExecutionId: Long? = null,
    ): EmailOutbox = EmailOutbox(
        recipientEmail = recipientEmail,
        recipientName = recipientName,
        subject = subject,
        emailType = emailType,
        deliveryStatus = deliveryStatus,
        messageId = messageId,
        sentAt = sentAt,
        attempts = if (deliveryStatus == EmailDeliveryStatus.PENDING) 0 else 1,
        jobExecutionId = jobExecutionId,
    )

    fun create(
        recipientEmail: String = "recipient@example.com",
        recipientName: String = "Test Recipient",
        subject: String = "Test Subject",
        emailType: String = "email.test",
        deliveryStatus: EmailDeliveryStatus = EmailDeliveryStatus.SENT,
        messageId: String? = "<test-msg-${System.nanoTime()}@test.blueshell.net>",
        sentAt: Instant? = Instant.now(),
        jobExecutionId: Long? = null,
    ): EmailOutbox = persistence.persist(
        build(recipientEmail, recipientName, subject, emailType, deliveryStatus, messageId, sentAt, jobExecutionId)
    )
}

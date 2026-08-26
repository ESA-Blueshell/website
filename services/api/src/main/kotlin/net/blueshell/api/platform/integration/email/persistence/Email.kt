package net.blueshell.api.platform.integration.email.persistence

import jakarta.persistence.*
import net.blueshell.api.shared.enums.ActionActorType
import net.blueshell.api.shared.enums.EmailDeliveryStatus
import net.blueshell.api.shared.model.AuditedAutoIdEntity
import java.time.Instant

@Entity
@Table(
    name = "emails",
    indexes = [
        Index(name = "idx_emails_status", columnList = "delivery_status"),
        Index(name = "idx_emails_email_type", columnList = "email_type"),
        Index(name = "idx_emails_recipient", columnList = "recipient_email"),
        Index(name = "idx_emails_sent_at", columnList = "sent_at"),
    ]
)
class Email(
    @Column(name = "recipient_email", nullable = false) val recipientEmail: String = "",
    @Column(name = "recipient_name", nullable = false) val recipientName: String = "",
    @Column(name = "subject", nullable = false) val subject: String = "",
    /**
     * The markdown body the sending domain produced, kept so a sent email can be read back.
     * Null for rows written before the column existed.
     */
    @Lob @Column(name = "body_markdown") val bodyMarkdown: String? = null,
    @Column(name = "email_type", nullable = false) val emailType: String = "",

    @Enumerated(EnumType.STRING) @Column(
        name = "delivery_status", nullable = false
    ) var deliveryStatus: EmailDeliveryStatus = EmailDeliveryStatus.PENDING,

    @Column(name = "message_id") var messageId: String? = null,
    /** Opaque UUID used as the tracking pixel token — never exposed in responses. */
    @Column(name = "tracking_token", unique = true) val trackingToken: String? = null,
    @Column(name = "sent_at") var sentAt: Instant? = null,
    @Column(name = "delivered_at") var deliveredAt: Instant? = null,
    @Column(name = "opened_at") var openedAt: Instant? = null,

    @Column(name = "error_type") var errorType: String? = null,
    @Lob @Column(name = "error_reason") var errorReason: String? = null,

    @Column(name = "attempts", nullable = false) var attempts: Int = 0,

    @Column(name = "job_execution_id") var jobExecutionId: Long? = null,

    @Column(name = "initiated_by_user_id") var initiatedByUserId: Long? = null,
    @Enumerated(EnumType.STRING) @Column(
        name = "initiated_by_type", nullable = false
    ) var initiatedByType: ActionActorType = ActionActorType.SYSTEM,
) : AuditedAutoIdEntity()

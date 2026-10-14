package net.blueshell.api.mail.persistence

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Lob
import jakarta.persistence.Table
import net.blueshell.api.shared.model.AutoIdEntity
import java.time.Instant

/** How far the board got with a received message. */
@Schema(name = "InboxState", enumAsRef = true)
enum class InboxState {
    NEW,
    REPLIED,
    HANDLED,
}

/** One message from the catch-all mailbox, kept once by its Message-ID. */
@Entity
@Table(name = "inbox_messages")
class InboxMessage(
    @Column(name = "message_id", nullable = false, unique = true, length = 512)
    val messageId: String,
    @Column(name = "in_reply_to", length = 512)
    val inReplyTo: String?,
    /** Every Message-ID the message names as its thread, In-Reply-To and References, space apart. */
    @Column(name = "thread_ids")
    val threadIds: String?,
    @Column(name = "from_address", nullable = false, length = 320)
    val fromAddress: String,
    @Column(name = "from_name")
    val fromName: String?,
    /** The address it was sent to, such as partners@, which the catch-all took for it. */
    @Column(name = "to_address", length = 320)
    val toAddress: String?,
    @Column(name = "subject", nullable = false, length = 998)
    val subject: String,
    @Lob
    @Column(name = "body_text")
    val bodyText: String?,
    @Lob
    @Column(name = "body_html")
    val bodyHtml: String?,
    @Column(name = "received_at", nullable = false)
    val receivedAt: Instant,
    /** An out-of-office or other automatic reply, kept apart from what needs an answer. */
    @Column(name = "automatic", nullable = false)
    val automatic: Boolean,
    @Column(name = "answers_email_id")
    val answersEmailId: Long?,
    @Column(name = "sender_user_id")
    val senderUserId: Long?,
) : AutoIdEntity() {
    @Enumerated(EnumType.STRING)
    @Column(name = "state", nullable = false, length = 16)
    var state: InboxState = InboxState.NEW

    @Column(name = "handled_by")
    var handledBy: Long? = null

    @Column(name = "handled_at")
    var handledAt: Instant? = null
}

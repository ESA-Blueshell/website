package net.blueshell.api.mail.persistence

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Lob
import jakarta.persistence.Table
import net.blueshell.api.shared.model.AutoIdEntity
import java.time.Instant

/** A reply the board wrote on the site to one received message. */
@Entity
@Table(name = "inbox_replies")
class InboxReply(
    @Column(name = "inbox_message_id", nullable = false)
    val inboxMessageId: Long,
    /** As the site's editor writes it, in Discord's markdown. */
    @Lob
    @Column(name = "message", nullable = false)
    val message: String,
    @Column(name = "reply_to")
    val replyTo: String?,
    @Column(name = "written_by")
    val writtenBy: Long?,
    @Column(name = "written_at", nullable = false)
    val writtenAt: Instant,
) : AutoIdEntity()

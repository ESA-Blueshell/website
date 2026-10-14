package net.blueshell.api.mail.persistence

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Lob
import jakarta.persistence.Table
import net.blueshell.api.shared.model.AutoIdEntity
import java.time.Instant

/** One email the board wrote, kept once however many it was sent to. */
@Entity
@Table(name = "written_emails")
class WrittenEmail(
    @Column(name = "subject", nullable = false)
    val subject: String,
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
    @Column(name = "recipients", nullable = false)
    val recipients: Int,
) : AutoIdEntity()

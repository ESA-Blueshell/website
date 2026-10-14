package net.blueshell.api.email.api

import net.blueshell.api.email.persistence.EmailRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

/** A sent email as another module names it: which one, what kind, to whom and when. */
data class SentEmailRef(
    val id: Long,
    val emailType: String,
    val recipientEmail: String,
    val subject: String,
    val sentAt: Instant?,
)

/** Finds the site's own emails by what a reply carries, its Message-ID, or by their id. */
@Service
class SentEmails(
    private val emails: EmailRepository,
) {
    /** The first of [messageIds] that names an email the site sent, read in the order given. */
    @Transactional(readOnly = true)
    fun answeredBy(messageIds: List<String>): SentEmailRef? {
        if (messageIds.isEmpty()) return null
        val found = emails.findByMessageIdIn(messageIds).associateBy { it.messageId }
        return messageIds.firstNotNullOfOrNull { found[it] }?.let(::refOf)
    }

    @Transactional(readOnly = true)
    fun byIds(ids: Collection<Long>): Map<Long, SentEmailRef> =
        if (ids.isEmpty()) emptyMap() else emails.findByIdIn(ids).map(::refOf).associateBy { it.id }

    private fun refOf(email: net.blueshell.api.email.persistence.Email) =
        SentEmailRef(requireNotNull(email.id), email.emailType, email.recipientEmail, email.subject, email.sentAt)
}

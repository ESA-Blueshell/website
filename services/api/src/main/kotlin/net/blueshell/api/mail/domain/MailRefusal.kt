package net.blueshell.api.mail.domain

import jakarta.mail.internet.InternetAddress
import net.blueshell.api.shared.refusal.Refusal
import org.springframework.http.HttpStatus

// A code and the facts, never the sentence: `domains/mail/refusals.ts` writes that. See ADR-026.
sealed class MailRefusal(
    status: HttpStatus,
    code: String,
    summary: String,
    facts: Map<String, Any> = emptyMap(),
) : Refusal(status, code, summary, facts)

class NobodyToWrite : MailRefusal(HttpStatus.BAD_REQUEST, "NobodyToWrite", "Nobody it is addressed to has an email address.")

class SubjectMissing : MailRefusal(HttpStatus.BAD_REQUEST, "SubjectMissing", "An email has a subject.")

class MessageMissing : MailRefusal(HttpStatus.BAD_REQUEST, "MessageMissing", "An email says something.")

class SubjectTooLong :
    MailRefusal(HttpStatus.BAD_REQUEST, "SubjectTooLong", "A subject is at most $SUBJECT_LIMIT characters.", mapOf("max" to SUBJECT_LIMIT))

class SubjectNotOneLine : MailRefusal(HttpStatus.BAD_REQUEST, "SubjectNotOneLine", "A subject is one line.")

class MessageTooLong :
    MailRefusal(HttpStatus.BAD_REQUEST, "MessageTooLong", "A message is at most $MESSAGE_LIMIT characters.", mapOf("max" to MESSAGE_LIMIT))

class InboxMessageNotFound : MailRefusal(HttpStatus.NOT_FOUND, "InboxMessageNotFound", "There is no such message in the inbox.")

class WriterWithoutAddress : MailRefusal(HttpStatus.BAD_REQUEST, "WriterWithoutAddress", "The writer has no email address for a test.")

// The subject column's width.
const val SUBJECT_LIMIT = 255

const val MESSAGE_LIMIT = 50_000

// The longest address SMTP carries.
private const val ADDRESS_LIMIT = 254

class ReplyToNotAnAddress(
    replyTo: String,
) : MailRefusal(HttpStatus.BAD_REQUEST, "ReplyToNotAnAddress", "The reply-to is not one email address.", mapOf("replyTo" to replyTo))

/** The reply-to as it is kept: none when blank, refused unless it is exactly one well-formed address. */
fun checkedReplyTo(replyTo: String?): String? {
    val written = replyTo?.trim()?.ifEmpty { null } ?: return null
    if (written.length > ADDRESS_LIMIT) throw ReplyToNotAnAddress(written)
    val parsed =
        runCatching { InternetAddress.parse(written, true) }
            .getOrNull()
            ?.takeIf { it.size == 1 && it.single().personal == null && it.single().address == written }
    // The parser takes a bare local name as an address; replies need a domain to reach.
    return parsed?.single()?.address?.takeIf(ADDRESS::matches) ?: throw ReplyToNotAnAddress(written)
}

private val ADDRESS = Regex("""[^@\s]+@[^@\s]+\.[^@\s]+""")

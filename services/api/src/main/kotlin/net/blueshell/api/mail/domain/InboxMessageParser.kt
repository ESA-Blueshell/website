package net.blueshell.api.mail.domain

import jakarta.mail.Address
import jakarta.mail.Message
import jakarta.mail.Multipart
import jakarta.mail.Part
import jakarta.mail.internet.InternetAddress
import java.time.Instant

/** A received message as the inbox keeps it. */
data class ParsedInboxMessage(
    val messageId: String,
    val inReplyTo: String?,
    /** In-Reply-To first, then References newest first: the order a reply's thread is searched in. */
    val threadIds: List<String>,
    val fromAddress: String,
    val fromName: String?,
    val toAddress: String?,
    val subject: String,
    val bodyText: String?,
    val bodyHtml: String?,
    val receivedAt: Instant,
    val automatic: Boolean,
)

/**
 * Reads a message from the catch-all mailbox. A delivery report is the bounce poller's and reads as
 * nothing here; an out-of-office or other automatic reply is kept, marked as one.
 */
object InboxMessageParser {
    private val MESSAGE_ID = Regex("<[^<>\\s]+>")
    private val AUTOMATIC_SUBJECT = Regex("^(automatic reply|auto(matic)?[- ]?reply|out of (the )?office|afwezig)", RegexOption.IGNORE_CASE)

    /** [fallbackId] stands in for a missing Message-ID, and must be the same every time the message is read. */
    fun parse(
        message: Message,
        fallbackId: String,
        fallbackReceivedAt: Instant,
    ): ParsedInboxMessage? {
        if (isDeliveryReport(message)) return null
        val from = message.from?.firstOrNull() as? InternetAddress ?: return null
        val inReplyTo = header(message, "In-Reply-To")?.let { MESSAGE_ID.find(it)?.value }
        val references =
            header(message, "References")
                ?.let {
                    MESSAGE_ID
                        .findAll(it)
                        .map { found ->
                            found.value
                        }.toList()
                        .reversed()
                }.orEmpty()
        val subject = message.subject.orEmpty()
        val (text, html) = bodiesOf(message)
        return ParsedInboxMessage(
            messageId = header(message, "Message-ID")?.let { MESSAGE_ID.find(it)?.value } ?: fallbackId,
            inReplyTo = inReplyTo,
            threadIds = (listOfNotNull(inReplyTo) + references).distinct(),
            fromAddress = from.address.lowercase(),
            fromName = from.personal,
            toAddress = deliveredTo(message),
            subject = subject,
            bodyText = text,
            bodyHtml = html,
            receivedAt = message.receivedDate?.toInstant() ?: message.sentDate?.toInstant() ?: fallbackReceivedAt,
            automatic = isAutomatic(message, subject),
        )
    }

    private fun header(
        message: Message,
        name: String,
    ): String? = message.getHeader(name)?.firstOrNull()

    private fun isDeliveryReport(message: Message): Boolean =
        message.isMimeType("multipart/report") ||
            (message.from?.firstOrNull() as? InternetAddress)?.address?.lowercase()?.startsWith("mailer-daemon@") == true

    // Auto-Submitted is the standard (RFC 3834); the others are what common mail servers send.
    private fun isAutomatic(
        message: Message,
        subject: String,
    ): Boolean {
        val submitted = header(message, "Auto-Submitted")?.trim()?.lowercase()
        val precedence = header(message, "Precedence")?.trim()?.lowercase()
        return (submitted != null && submitted != "no") ||
            header(message, "X-Autoreply") != null ||
            header(message, "X-Autorespond") != null ||
            precedence in setOf("auto_reply", "bulk", "junk") ||
            AUTOMATIC_SUBJECT.containsMatchIn(subject)
    }

    // The address the catch-all took it for: what the server says it delivered to, else the first To.
    private fun deliveredTo(message: Message): String? =
        (header(message, "Delivered-To") ?: header(message, "X-Original-To"))?.trim()?.lowercase()
            ?: message.getRecipients(Message.RecipientType.TO)?.firstOrNull()?.let(::addressOf)

    private fun addressOf(address: Address): String? = (address as? InternetAddress)?.address?.lowercase()

    private fun bodiesOf(part: Part): Pair<String?, String?> {
        var text: String? = null
        var html: String? = null

        fun walk(one: Part) {
            when {
                one.isMimeType("text/plain") && text == null -> text = one.content as? String
                one.isMimeType("text/html") && html == null -> html = one.content as? String
                one.isMimeType("multipart/*") ->
                    (one.content as? Multipart)?.let { parts ->
                        for (at in 0 until parts.count) walk(parts.getBodyPart(at))
                    }
            }
        }
        walk(part)
        return text to html
    }
}

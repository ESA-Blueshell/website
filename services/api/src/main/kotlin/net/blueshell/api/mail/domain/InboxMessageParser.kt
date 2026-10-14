package net.blueshell.api.mail.domain

import jakarta.mail.Address
import jakarta.mail.Message
import jakarta.mail.Multipart
import jakarta.mail.Part
import jakarta.mail.internet.ContentType
import jakarta.mail.internet.InternetAddress
import jakarta.mail.internet.MimeUtility
import java.nio.charset.Charset
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

/** A message the inbox cannot keep, such as one without a sender's address. */
class UnreadableMessage(
    reason: String,
) : RuntimeException(reason)

/**
 * Reads a message from the catch-all mailbox. A delivery report is the bounce poller's and reads as
 * nothing here; an out-of-office or other automatic reply is kept, marked as one.
 */
object InboxMessageParser {
    // Widths of the columns these land in; an id longer than its column is no id the site made.
    private const val ID_LIMIT = 512
    private const val ADDRESS_LIMIT = 320
    private const val NAME_LIMIT = 255
    private const val SUBJECT_LIMIT = 998
    private const val THREAD_LIMIT = 50

    /** What of each body is kept; a longer one is cut there and never read past it. */
    const val BODY_LIMIT = 65_536

    // A real message nests a few parts deep; a deeper one is read no further.
    private const val DEPTH_LIMIT = 10

    private val PLAIN_ADDRESS = Regex("[^\\s@\"]+@[^\\s@\"]+")
    private val LINE_BREAKS = Regex("[\\r\\n]+")
    private val MESSAGE_ID = Regex("<[^<>\\s]{1,${ID_LIMIT - 2}}>")
    private val AUTOMATIC_SUBJECT =
        Regex("^(automatic reply|auto(matic)?[- ]?reply|out of (the )?office|afwezig)\\s*:", RegexOption.IGNORE_CASE)

    /** [fallbackId] stands in for a missing Message-ID, and must be the same every time the message is read. */
    fun parse(
        message: Message,
        fallbackId: String,
        fallbackReceivedAt: Instant,
    ): ParsedInboxMessage? {
        if (isDeliveryReport(message)) return null
        val from = senderOf(message) ?: throw UnreadableMessage("no sender")
        // A reply goes to this address, so it is one plain address or the message is not kept.
        val fromAddress =
            from.address?.lowercase()?.takeIf { it.length <= ADDRESS_LIMIT && PLAIN_ADDRESS.matches(it) }
                ?: throw UnreadableMessage("no usable sender")
        val inReplyTo = idIn(header(message, "In-Reply-To"))
        val references = idsIn(header(message, "References")).reversed()
        // One line: a decoded subject can hold line breaks, and a reply repeats it as a header.
        val subject = oneLine(message.subject.orEmpty()).take(SUBJECT_LIMIT)
        val (text, html) = bodiesOf(message)
        return ParsedInboxMessage(
            messageId = idIn(header(message, "Message-ID")) ?: fallbackId,
            inReplyTo = inReplyTo,
            threadIds = (listOfNotNull(inReplyTo) + references).distinct().take(THREAD_LIMIT),
            fromAddress = fromAddress,
            fromName = from.personal?.let(::oneLine)?.take(NAME_LIMIT),
            toAddress = deliveredTo(message),
            subject = subject,
            bodyText = text,
            bodyHtml = html,
            receivedAt = message.receivedDate?.toInstant() ?: message.sentDate?.toInstant() ?: fallbackReceivedAt,
            automatic = isAutomatic(message, subject),
        )
    }

    private fun oneLine(text: String) = text.replace(LINE_BREAKS, " ")

    // A From header that does not parse names no sender.
    private fun senderOf(message: Message): InternetAddress? = runCatching { message.from }.getOrNull()?.firstOrNull() as? InternetAddress

    // An id longer than its column does not match at all, rather than in part.
    private fun idIn(value: String?): String? = value?.let { MESSAGE_ID.find(it)?.value }

    private fun idsIn(value: String?): List<String> = value?.let { MESSAGE_ID.findAll(it).map(MatchResult::value).toList() }.orEmpty()

    private fun header(
        message: Message,
        name: String,
    ): String? = message.getHeader(name)?.firstOrNull()

    private fun isDeliveryReport(message: Message): Boolean =
        message.isMimeType("multipart/report") ||
            senderOf(message)?.address?.lowercase()?.startsWith("mailer-daemon@") == true

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
    private fun deliveredTo(message: Message): String? {
        val delivered = (header(message, "Delivered-To") ?: header(message, "X-Original-To"))?.let(::parsedAddress)
        val address = delivered ?: message.getRecipients(Message.RecipientType.TO)?.firstOrNull()?.let(::addressOf)
        return address?.takeIf { it.length <= ADDRESS_LIMIT }
    }

    private fun parsedAddress(header: String): String? =
        runCatching { InternetAddress.parse(header, false).firstOrNull() }.getOrNull()?.let(::addressOf)

    private fun addressOf(address: Address): String? = (address as? InternetAddress)?.address?.lowercase()

    private fun bodiesOf(part: Part): Pair<String?, String?> {
        var text: String? = null
        var html: String? = null

        fun walk(
            one: Part,
            depth: Int,
        ) {
            when {
                one.isMimeType("text/plain") && text == null -> text = textOf(one)
                one.isMimeType("text/html") && html == null -> html = textOf(one)
                one.isMimeType("multipart/*") && depth < DEPTH_LIMIT ->
                    (one.content as? Multipart)?.let { parts ->
                        for (at in 0 until parts.count) walk(parts.getBodyPart(at), depth + 1)
                    }
            }
        }
        walk(part, 0)
        return text to html
    }

    // Read up to what is kept, never the whole part: a part can be any size.
    private fun textOf(part: Part): String {
        val charset =
            runCatching { Charset.forName(MimeUtility.javaCharset(ContentType(part.contentType).getParameter("charset"))) }
                .getOrDefault(Charsets.UTF_8)
        val bytes = part.inputStream.use { it.readNBytes(BODY_LIMIT * MAX_BYTES_PER_CHAR) }
        return String(bytes, charset).take(BODY_LIMIT)
    }

    private const val MAX_BYTES_PER_CHAR = 4
}

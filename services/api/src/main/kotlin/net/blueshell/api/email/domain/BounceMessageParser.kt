package net.blueshell.api.email.domain

import jakarta.mail.Message
import jakarta.mail.Multipart
import jakarta.mail.Part
import jakarta.mail.internet.MimeBodyPart
import jakarta.mail.internet.MimeMultipart
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Parses an RFC 3464 delivery-status notification into a [ParsedBounce].
 *
 * A bounce is a `multipart/report; report-type=delivery-status` message in three parts: a
 * human-readable explanation, a `message/delivery-status` part carrying `Original-Message-ID`,
 * `Action`, `Status` and `Diagnostic-Code`, and the rejected message or its headers. Pure, so it
 * unit-tests against canned MIME fixtures without an IMAP server.
 */
object BounceMessageParser {

    /** Returns null when the message is not a DSN or has no usable `Original-Message-ID`. */
    fun parse(message: Message): ParsedBounce? {
        if (!isDsn(message)) return null

        val parts = mimePartsOf(message)
        val deliveryStatusBody = parts
            .firstOrNull { it.isMimeType("message/delivery-status") }
            ?.let { readBody(it) }
            ?: return null

        val originalMessageId = extractHeader(deliveryStatusBody, "Original-Message-ID")
            ?: extractOriginalMessageIdFromAttachedMessage(parts)
            ?: return null

        val diagnostic = extractHeader(deliveryStatusBody, "Diagnostic-Code")
        val action = extractHeader(deliveryStatusBody, "Action")
        val status = extractHeader(deliveryStatusBody, "Status")
        val finalRecipient = extractHeader(deliveryStatusBody, "Final-Recipient")
            ?.substringAfter(';', missingDelimiterValue = "")
            ?.trim()
            ?.ifEmpty { null }

        return ParsedBounce(
            originalMessageId = normaliseMessageId(originalMessageId),
            recipient = finalRecipient,
            action = action,
            status = status,
            diagnostic = diagnostic,
        )
    }

    private fun isDsn(message: Message): Boolean {
        val contentType = message.contentType ?: return false
        return contentType.contains("multipart/report", ignoreCase = true) &&
            contentType.contains("delivery-status", ignoreCase = true)
    }

    private fun mimePartsOf(message: Message): List<Part> {
        val content = message.content
        if (content !is Multipart) return emptyList()
        return (0 until content.count).map { content.getBodyPart(it) }
    }

    private fun readBody(part: Part): String {
        val raw = part.content
        if (raw is String) return raw
        return part.inputStream.use { stream ->
            BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).readText()
        }
    }

    private fun extractHeader(text: String, name: String): String? {
        val prefix = "$name:"
        return text.lineSequence()
            .map { it.trimEnd('\r') }
            .firstOrNull { it.startsWith(prefix, ignoreCase = true) }
            ?.substringAfter(':', missingDelimiterValue = "")
            ?.trim()
            ?.ifEmpty { null }
    }

    /**
     * Some MTAs (Postfix in particular) omit `Original-Message-ID` from the
     * `message/delivery-status` part and include the full original message or
     * just its headers as a third part. Walk that part for `Message-ID`.
     */
    private fun extractOriginalMessageIdFromAttachedMessage(parts: List<Part>): String? {
        val attached = parts.firstOrNull {
            it.isMimeType("message/rfc822") || it.isMimeType("text/rfc822-headers")
        } ?: return null
        val body = when (val content = attached.content) {
            is Message -> content.allHeaders.asSequence()
                .filter { it.name.equals("Message-ID", ignoreCase = true) }
                .map { "Message-ID: ${it.value}" }
                .joinToString("\n")
            is MimeBodyPart -> readBody(content)
            is MimeMultipart -> (0 until content.count)
                .joinToString("\n") { readBody(content.getBodyPart(it)) }
            else -> readBody(attached)
        }
        return extractHeader(body, "Message-ID")
    }

    private fun normaliseMessageId(raw: String): String =
        "<${raw.trim().trim('<', '>')}>"

    /**
     * Value object representing a parsed bounce. [originalMessageId] is normalised
     * with surrounding angle brackets so it matches the value [SmtpEmailClient]
     * persists on the outbox row.
     */
    data class ParsedBounce(
        val originalMessageId: String,
        val recipient: String?,
        val action: String?,
        val status: String?,
        val diagnostic: String?,
    ) {
        fun describe(): String = listOfNotNull(
            diagnostic?.let { "diagnostic=$it" },
            status?.let { "status=$it" },
            action?.let { "action=$it" },
        ).joinToString(" ").ifEmpty { "DSN received with no diagnostic" }
    }
}

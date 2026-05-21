package net.blueshell.api.platform.integration.email.adapter

import jakarta.mail.Message
import jakarta.mail.Multipart
import jakarta.mail.Part
import jakarta.mail.internet.MimeBodyPart
import jakarta.mail.internet.MimeMultipart
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Parses a DSN ([RFC 3464](https://datatracker.ietf.org/doc/html/rfc3464))
 * delivery-status notification into a [ParsedBounce].
 *
 * Bounces arrive at the bounce mailbox as `multipart/report; report-type=delivery-status`
 * MIME messages with three parts:
 *  - a human-readable explanation,
 *  - a `message/delivery-status` part with headers including `Original-Message-ID`,
 *    `Action`, `Status`, `Diagnostic-Code`,
 *  - the rejected original message (or its headers).
 *
 * The parser is pure — it only reads from a [Message] and returns a value object —
 * so it can be unit-tested against canned MIME fixtures without standing up a real
 * IMAP server.
 */
object BounceMessageParser {

    /**
     * Parses a DSN [Message] into a [ParsedBounce]. Returns `null` when the message
     * is not a DSN, or when no usable `Original-Message-ID` can be extracted.
     */
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
            originalMessageId = originalMessageId.trim().trim('<', '>')
                .let { "<$it>" },
            recipient = finalRecipient,
            action = action,
            status = status,
            diagnostic = diagnostic,
        )
    }

    private fun isDsn(message: Message): Boolean {
        val contentType = message.contentType ?: return false
        // contentType looks like: multipart/report; report-type=delivery-status; boundary="..."
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
     * Some MTAs (notably Postfix) omit `Original-Message-ID` from the
     * `message/delivery-status` part and instead include the full original
     * message as a third part. Walk that part's headers looking for `Message-ID`.
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

    /**
     * Value object representing a parsed bounce. [originalMessageId] is normalised
     * to include the surrounding angle brackets so it matches what
     * [SmtpEmailClient] persists on the outbox row.
     */
    data class ParsedBounce(
        val originalMessageId: String,
        val recipient: String?,
        val action: String?,
        val status: String?,
        val diagnostic: String?,
    ) {
        /** Human-readable diagnostic, falling back to action/status when no diagnostic code was set. */
        fun describe(): String = listOfNotNull(
            diagnostic?.let { "diagnostic=$it" },
            status?.let { "status=$it" },
            action?.let { "action=$it" },
        ).joinToString(" ")
            .ifEmpty { "DSN received with no diagnostic" }
    }
}

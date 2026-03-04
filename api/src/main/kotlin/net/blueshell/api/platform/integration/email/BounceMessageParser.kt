package net.blueshell.api.platform.integration.email

import jakarta.mail.BodyPart
import jakarta.mail.Session
import jakarta.mail.internet.MimeMessage
import jakarta.mail.internet.MimeMultipart
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.util.Properties

/**
 * Parses raw email bytes into a [ParsedBounce] if the message is a bounce/DSN notification.
 * Returns null for non-bounce messages (auto-replies, OOO, regular mail).
 *
 * Supports two detection paths:
 * 1. RFC 3464 DSN (multipart/report with message/delivery-status part)
 * 2. Heuristic fallback for non-standard bounce notifications
 */
@Component
class BounceMessageParser {

    private val logger = LoggerFactory.getLogger(BounceMessageParser::class.java)

    private val oooPatterns = listOf(
        "out of office", "on vacation", "automatic reply", "auto-reply",
        "autoreply", "i am away", "i'm away", "away from", "currently out",
        "maternity leave", "paternity leave", "holiday notice",
    )

    private val bounceSubjectPatterns = listOf(
        "undeliverable", "undelivered", "mail delivery failed",
        "delivery status notification", "delivery failure",
        "returned mail", "failure notice", "non-delivery",
        "delivery problem", "mail system error",
    )

    private val messageIdRegex = Regex("<[^>]+>")

    fun parse(message: RawBounceMessage): ParsedBounce? {
        val session = Session.getInstance(Properties())
        val mimeMessage = MimeMessage(session, ByteArrayInputStream(message.rawBytes))

        // Filter out auto-replies and OOO messages
        val autoSubmitted = mimeMessage.getHeader("Auto-Submitted")?.firstOrNull()
        if (autoSubmitted != null && autoSubmitted != "no" && !autoSubmitted.contains("auto-generated")) {
            // auto-replied, auto-notified, etc. — skip unless auto-generated (DSNs use this)
            if (!isDsnContentType(message.contentType)) {
                logger.debug("Skipping auto-submitted message: subject='{}'", message.subject)
                return null
            }
        }

        val subjectLower = message.subject.lowercase()
        if (oooPatterns.any { subjectLower.contains(it) }) {
            logger.debug("Skipping OOO message: subject='{}'", message.subject)
            return null
        }

        // Path 1: RFC 3464 DSN (multipart/report)
        if (isDsnContentType(message.contentType)) {
            return parseDsn(mimeMessage, message.subject)
        }

        // Path 2: Heuristic fallback for non-standard bounce notifications
        if (bounceSubjectPatterns.any { subjectLower.contains(it) }) {
            return parseHeuristic(mimeMessage, message.subject)
        }

        return null
    }

    private fun isDsnContentType(contentType: String): Boolean =
        contentType.lowercase().contains("multipart/report")

    private fun parseDsn(mimeMessage: MimeMessage, subject: String): ParsedBounce? {
        try {
            val content = mimeMessage.content
            if (content !is MimeMultipart) return null

            var status: String? = null
            var action: String? = null
            var diagnosticCode: String? = null
            var originalMessageId: String? = null

            for (i in 0 until content.count) {
                val part = content.getBodyPart(i)
                val partContentType = part.contentType.lowercase()

                when {
                    partContentType.contains("message/delivery-status") -> {
                        val dsnText = extractTextContent(part)
                        status = extractDsnField(dsnText, "Status")
                        action = extractDsnField(dsnText, "Action")
                        diagnosticCode = extractDsnField(dsnText, "Diagnostic-Code")
                    }
                    partContentType.contains("message/rfc822") || partContentType.contains("text/rfc822-headers") -> {
                        originalMessageId = extractOriginalMessageId(part)
                    }
                }
            }

            // Fallback: try top-level headers for original message ID
            if (originalMessageId == null) {
                originalMessageId = mimeMessage.getHeader("X-Failed-Recipients")?.firstOrNull()
                    ?: extractMessageIdFromBody(mimeMessage)
            }

            if (originalMessageId == null) {
                logger.debug("DSN without original Message-ID, subject='{}'", subject)
                return null
            }

            return ParsedBounce(
                originalMessageId = normalizeMessageId(originalMessageId),
                status = status,
                action = mapAction(action),
                diagnosticCode = diagnosticCode,
                rawSubject = subject,
            )
        } catch (e: Exception) {
            logger.warn("Failed to parse DSN message: subject='{}'", subject, e)
            return null
        }
    }

    private fun parseHeuristic(mimeMessage: MimeMessage, subject: String): ParsedBounce? {
        val messageId = extractMessageIdFromBody(mimeMessage) ?: return null

        return ParsedBounce(
            originalMessageId = normalizeMessageId(messageId),
            status = null,
            action = BounceAction.FAILED,
            diagnosticCode = null,
            rawSubject = subject,
        )
    }

    private fun extractDsnField(dsnText: String, fieldName: String): String? {
        val regex = Regex("(?i)^$fieldName:\\s*(.+)", RegexOption.MULTILINE)
        return regex.find(dsnText)?.groupValues?.get(1)?.trim()
    }

    private fun extractOriginalMessageId(part: BodyPart): String? {
        return try {
            val content = part.content
            if (content is MimeMessage) {
                content.messageID
            } else {
                // text/rfc822-headers — parse as text
                val text = extractTextContent(part)
                val regex = Regex("(?i)^Message-ID:\\s*(.+)", RegexOption.MULTILINE)
                regex.find(text)?.groupValues?.get(1)?.trim()
            }
        } catch (e: Exception) {
            logger.debug("Could not extract original Message-ID from part", e)
            null
        }
    }

    private fun extractMessageIdFromBody(mimeMessage: MimeMessage): String? {
        return try {
            val bodyText = extractFullText(mimeMessage)
            val match = Regex("(?i)Message-ID:\\s*(${messageIdRegex.pattern})").find(bodyText)
            match?.groupValues?.get(1)
        } catch (e: Exception) {
            logger.debug("Could not extract Message-ID from body", e)
            null
        }
    }

    private fun extractTextContent(part: BodyPart): String {
        val content = part.content
        return when (content) {
            is String -> content
            is InputStream -> content.bufferedReader().readText()
            is MimeMultipart -> {
                val sb = StringBuilder()
                for (i in 0 until content.count) {
                    sb.append(extractTextContent(content.getBodyPart(i)))
                    sb.append("\n")
                }
                sb.toString()
            }
            else -> content.toString()
        }
    }

    private fun extractFullText(mimeMessage: MimeMessage): String {
        val content = mimeMessage.content
        return when (content) {
            is String -> content
            is MimeMultipart -> {
                val sb = StringBuilder()
                for (i in 0 until content.count) {
                    val part = content.getBodyPart(i)
                    val partType = part.contentType.lowercase()
                    if (partType.contains("text/plain") || partType.contains("text/html")) {
                        sb.append(part.content.toString())
                        sb.append("\n")
                    }
                }
                sb.toString()
            }
            else -> content.toString()
        }
    }

    private fun mapAction(action: String?): BounceAction {
        return when (action?.lowercase()?.trim()) {
            "failed" -> BounceAction.FAILED
            "delayed" -> BounceAction.DELAYED
            else -> BounceAction.OTHER
        }
    }

    private fun normalizeMessageId(messageId: String): String {
        val trimmed = messageId.trim()
        return if (trimmed.startsWith("<") && trimmed.endsWith(">")) {
            trimmed
        } else {
            "<$trimmed>"
        }
    }
}

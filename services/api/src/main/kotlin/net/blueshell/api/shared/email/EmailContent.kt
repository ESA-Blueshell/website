package net.blueshell.api.shared.email

/**
 * Email content prepared by a domain for the platform email transport.
 *
 * The sender address always comes from `email.from.address` configuration —
 * it has to match what the SMTP relay accepts. The display name and reply-to
 * default to `email.from.name` / `email.reply-to`; per-flow overrides exist
 * here for semantic labels like "Treasurer of Blueshell" or board-only reply
 * addresses on activation emails.
 */
data class EmailContent(
    val recipientEmail: String,
    val recipientName: String,
    val subject: String,
    val markdownContent: String,
    val senderNameOverride: String? = null,
    val replyToOverride: String? = null,
)

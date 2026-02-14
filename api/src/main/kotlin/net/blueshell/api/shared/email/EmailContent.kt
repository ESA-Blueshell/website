package net.blueshell.api.shared.email

/**
 * Email content DTO used as Anti-Corruption Layer (ADR-019) between domains and platform email system.
 *
 * Domains build EmailContent objects with markdown body, platform handles rendering and delivery.
 */
data class EmailContent(
    val recipientEmail: String,
    val recipientName: String,
    val subject: String,
    val markdownContent: String,
    val senderName: String = "Blueshell",
    val senderAddress: String = "sitecie@blueshell.utwente.nl",
    val replyTo: String = "sitecie@blueshell.utwente.nl"
)

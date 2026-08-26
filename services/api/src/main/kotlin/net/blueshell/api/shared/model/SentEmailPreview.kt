package net.blueshell.api.shared.model

/**
 * An email from the outbox, rendered for inspection with its URLs stripped out.
 *
 * Distinct from [RenderedEmailPreview], which is an email built for sending and read before
 * it goes: this one was already sent, so its links are live credentials and none of them
 * survive into what a reader receives.
 */
data class SentEmailPreview(
    val subject: String,
    val html: String,
    val recipientEmail: String,
    val recipientName: String,
)

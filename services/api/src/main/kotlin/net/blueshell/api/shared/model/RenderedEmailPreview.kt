package net.blueshell.api.shared.model

/**
 * An email rendered for inspection rather than delivery: the subject and body a recipient
 * would receive, with the hosted images inlined so the preview shows them.
 */
data class RenderedEmailPreview(
    val subject: String,
    val html: String,
)

package net.blueshell.api.shared.email

/**
 * Email content prepared by a domain for the platform email transport.
 *
 * The sender address comes from `email.from.address` configuration, which the site's
 * relay accepts, unless a sending address an admin added is named. The display name and reply-to
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
    /** The message this answers, so a mail client threads it with the conversation. */
    val inReplyTo: String? = null,
    /** The conversation's Message-IDs, oldest first, as the References header carries them. */
    val references: List<String> = emptyList(),
    /** An added sending address it goes out from (api ADR-039); none sends from the site's own. */
    val sendingAddressId: Long? = null,
) {
    /** The headers that thread this email with a conversation; none for an email that starts one. */
    val threadHeaders: Map<String, String>
        get() =
            buildMap {
                inReplyTo?.let { put("In-Reply-To", it) }
                if (references.isNotEmpty()) put("References", references.joinToString(" "))
            }
}

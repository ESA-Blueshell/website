package net.blueshell.api.shared.model

import net.blueshell.api.shared.enums.TokenPurpose

/**
 * A recovery email rendered for inspection rather than delivery.
 *
 * No token was issued to produce it: `linkPlaceholder` is what the recovery link carries
 * in place of one, so the operator can see that the link in front of them is inert.
 */
data class RecoveryEmailPreview(
    val purpose: TokenPurpose,
    val subject: String,
    val html: String,
    val recipientEmail: String,
    val recipientName: String,
    val linkPlaceholder: String,
)

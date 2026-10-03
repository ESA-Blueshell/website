package net.blueshell.api.user.api

/** A board member opened the full IBAN on the mandate of [membershipId], which is [userId]'s. Never carries the IBAN. */
data class IbanRevealed(
    val userId: Long,
    val membershipId: Long,
    val revealedBy: Long,
)

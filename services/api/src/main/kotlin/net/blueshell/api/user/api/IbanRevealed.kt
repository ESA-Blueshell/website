package net.blueshell.api.user.api

/** A board member opened [userId]'s full IBAN, with the [reference] of their mandate if they have one. Never carries the IBAN. */
data class IbanRevealed(
    val userId: Long,
    val reference: String?,
    val revealedBy: Long,
)

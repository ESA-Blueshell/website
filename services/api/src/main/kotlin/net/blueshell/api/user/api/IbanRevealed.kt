package net.blueshell.api.user.api

/** A board member opened the full IBAN on [userId]'s mandate, known by its [reference]. Never carries the IBAN. */
data class IbanRevealed(
    val userId: Long,
    val reference: String,
    val revealedBy: Long,
)

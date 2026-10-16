package net.blueshell.api.user.api

/** A person's mandate ended, so they pay by transfer from now on and are told so. */
data class MandateEnded(
    val userId: Long,
    val reference: String,
)

package net.blueshell.api.shared.model

import java.time.Instant

data class SignupSession(
    val userId: Long,
    val email: String,
    // Raw selector.verifier — the only plain-text copy; the store keeps a hash.
    val token: String,
    val expiresAt: Instant,
)

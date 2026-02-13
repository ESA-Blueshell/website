package net.blueshell.api.domain.auth.domain.service

/**
 * Abstraction for issuing authentication tokens.
 * Keeps domain/application code independent from infrastructure details.
 */
interface TokenGenerator {
    val expirationMs: Long

    fun generateToken(username: String): String
}

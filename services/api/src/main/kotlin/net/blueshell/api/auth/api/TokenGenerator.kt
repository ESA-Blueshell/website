package net.blueshell.api.auth.api

/**
 * Abstraction for issuing authentication tokens.
 * Keeps domain/application code independent from infrastructure details.
 */
interface TokenGenerator {
    val expirationMs: Long

    fun generateToken(username: String): String
}

package net.blueshell.api.auth.domain.twofactor

import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Ten codes of ten characters, about fifty bits each, which puts guessing out of reach without a
 * slow hash. Stored as SHA-256 of the code as typed, lower-cased and without separators.
 */
object BackupCodes {
    private const val COUNT = 10
    private const val GROUP = 5
    // No 0, 1, i, l or o, which read as each other.
    private const val ALPHABET = "abcdefghjkmnpqrstuvwxyz23456789"
    private val random = SecureRandom()
    private val shape = Regex("[a-z0-9]{10}")

    fun generate(): List<String> =
        generateSequence { code() }.distinct().take(COUNT).toList()

    fun hash(typed: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(normalise(typed).toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }

    fun looksLikeOne(typed: String): Boolean = shape.matches(normalise(typed))

    private fun normalise(typed: String): String = typed.lowercase().filter { it.isLetterOrDigit() }

    private fun code(): String {
        val chars = (1..GROUP * 2).map { ALPHABET[random.nextInt(ALPHABET.length)] }.joinToString("")
        return "${chars.take(GROUP)}-${chars.drop(GROUP)}"
    }
}

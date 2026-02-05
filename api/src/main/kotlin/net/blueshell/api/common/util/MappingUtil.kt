package net.blueshell.api.common.util

import java.security.SecureRandom

object MappingUtil {
    private const val CHAR_SET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!@#$%&*()-_=+<>?"
    private const val PASSWORD_LENGTH = 12
    private val random = SecureRandom()
    private const val CAPITAL_ALNUM = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"

    fun generateRandomString(length: Int = PASSWORD_LENGTH): String {
        require(length >= 0) { "length must be >= 0" }

        return buildString(length) {
            repeat(length) {
                append(CHAR_SET[random.nextInt(CHAR_SET.length)])
            }
        }
    }

    fun randomCapitalString(length: Int): String {
        require(length >= 0) { "length must be >= 0" }

        return buildString(length) {
            repeat(length) {
                append(CAPITAL_ALNUM[random.nextInt(CAPITAL_ALNUM.length)])
            }
        }
    }

    /**
     * Returns a random Int in 0..inclBound (inclusive).
     */
    fun randomInclusive(inclBound: Int): Int {
        require(inclBound >= 0) { "inclBound must be >= 0" }
        return random.nextInt(inclBound + 1)
    }
}

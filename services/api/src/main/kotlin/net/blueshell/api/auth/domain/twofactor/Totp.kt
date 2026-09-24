package net.blueshell.api.auth.domain.twofactor

import java.io.ByteArrayOutputStream
import java.net.URLEncoder
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Instant
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/** RFC 6238 with the parameters every authenticator app defaults to: SHA-1, six digits, thirty seconds. */
// The numbers are RFC 4226's dynamic truncation, bit for bit.
@Suppress("MagicNumber")
object Totp {
    private const val PERIOD_SECONDS = 30L
    private const val DIGITS = 6
    private const val SECRET_BYTES = 20
    private const val SKEW_STEPS = 1
    private val random = SecureRandom()
    private val sixDigits = Regex("\\d{6}")

    fun newSecret(): ByteArray = ByteArray(SECRET_BYTES).also(random::nextBytes)

    fun stepAt(instant: Instant): Long = Math.floorDiv(instant.epochSecond, PERIOD_SECONDS)

    fun code(
        secret: ByteArray,
        step: Long,
    ): String {
        val mac = Mac.getInstance("HmacSHA1").apply { init(SecretKeySpec(secret, "HmacSHA1")) }
        val hash = mac.doFinal(ByteBuffer.allocate(Long.SIZE_BYTES).putLong(step).array())
        val offset = hash.last().toInt() and 0x0f
        val binary =
            ((hash[offset].toInt() and 0x7f) shl 24) or
                ((hash[offset + 1].toInt() and 0xff) shl 16) or
                ((hash[offset + 2].toInt() and 0xff) shl 8) or
                (hash[offset + 3].toInt() and 0xff)
        return (binary % 1_000_000).toString().padStart(DIGITS, '0')
    }

    /**
     * The time step [code] belongs to, within one step either way of [now], provided it is later
     * than [lastUsedStep]; null for a wrong code or a replay. Compared in constant time.
     */
    fun acceptedStep(
        secret: ByteArray,
        code: String,
        now: Instant,
        lastUsedStep: Long?,
    ): Long? {
        val typed = code.filterNot { it.isWhitespace() }
        if (!sixDigits.matches(typed)) return null
        val current = stepAt(now)
        return (current - SKEW_STEPS..current + SKEW_STEPS)
            .filter { lastUsedStep == null || it > lastUsedStep }
            .firstOrNull { MessageDigest.isEqual(code(secret, it).toByteArray(), typed.toByteArray()) }
    }

    fun otpauthUri(
        issuer: String,
        account: String,
        base32Secret: String,
    ): String {
        val label = "${encode(issuer)}:${encode(account)}"
        return "otpauth://totp/$label?secret=$base32Secret&issuer=${encode(issuer)}&algorithm=SHA1&digits=$DIGITS&period=$PERIOD_SECONDS"
    }

    private fun encode(value: String) = URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20")
}

/** RFC 4648 base32 without padding, the form authenticator apps take a key in. */
// Five bits to a character and eight to a byte, which is the whole of the format.
@Suppress("MagicNumber")
object Base32 {
    private const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"

    fun encode(bytes: ByteArray): String {
        val out = StringBuilder()
        var buffer = 0
        var bits = 0
        for (byte in bytes) {
            buffer = (buffer shl 8) or (byte.toInt() and 0xff)
            bits += 8
            while (bits >= 5) {
                out.append(ALPHABET[(buffer shr (bits - 5)) and 0x1f])
                bits -= 5
            }
        }
        if (bits > 0) out.append(ALPHABET[(buffer shl (5 - bits)) and 0x1f])
        return out.toString()
    }

    fun decode(text: String): ByteArray {
        val out = ByteArrayOutputStream()
        var buffer = 0
        var bits = 0
        for (char in text.trimEnd('=').uppercase()) {
            val value = ALPHABET.indexOf(char)
            require(value >= 0) { "Not base32" }
            buffer = (buffer shl 5) or value
            bits += 5
            if (bits >= 8) {
                out.write((buffer shr (bits - 8)) and 0xff)
                bits -= 8
            }
        }
        return out.toByteArray()
    }
}

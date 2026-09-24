package net.blueshell.systemtests

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * What an authenticator app shows for a key, so a test can answer the code step. The api refuses a
 * code whose thirty-second step it has already taken, so a test giving a second code waits for
 * the next step rather than moving the api's clock under every other test.
 */
object TotpCodes {
    private const val PERIOD_MS = 30_000L
    private const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"

    fun now(base32Key: String): String = at(base32Key, System.currentTimeMillis() / PERIOD_MS)

    /** Waits into the next time step, and a moment more so the api's clock agrees. */
    fun awaitNextStep() {
        val intoStep = System.currentTimeMillis() % PERIOD_MS
        Thread.sleep(PERIOD_MS - intoStep + 500)
    }

    private fun at(
        base32Key: String,
        step: Long,
    ): String {
        val mac = Mac.getInstance("HmacSHA1").apply { init(SecretKeySpec(decode(base32Key), "HmacSHA1")) }
        val hash = mac.doFinal(ByteBuffer.allocate(8).putLong(step).array())
        val offset = hash.last().toInt() and 0x0f
        val binary =
            ((hash[offset].toInt() and 0x7f) shl 24) or
                ((hash[offset + 1].toInt() and 0xff) shl 16) or
                ((hash[offset + 2].toInt() and 0xff) shl 8) or
                (hash[offset + 3].toInt() and 0xff)
        return (binary % 1_000_000).toString().padStart(6, '0')
    }

    private fun decode(text: String): ByteArray {
        val out = ByteArrayOutputStream()
        var buffer = 0
        var bits = 0
        for (char in text.uppercase()) {
            buffer = (buffer shl 5) or ALPHABET.indexOf(char)
            bits += 5
            if (bits >= 8) {
                out.write((buffer shr (bits - 8)) and 0xff)
                bits -= 8
            }
        }
        return out.toByteArray()
    }
}

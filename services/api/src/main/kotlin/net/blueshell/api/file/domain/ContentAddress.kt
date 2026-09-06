package net.blueshell.api.file.domain

import java.io.InputStream
import java.io.OutputStream
import java.security.DigestInputStream
import java.security.MessageDigest
import java.util.HexFormat

/**
 * The address bytes are stored at: their SHA-256, in lower-case hex.
 *
 * Content-addressing is what makes storing the same picture twice cost one copy and makes a
 * served url safe to cache forever. Read in one pass and never held in memory, because an
 * upload is up to fifteen megabytes and there may be several at once.
 */
object ContentAddress {

    fun of(content: InputStream): String {
        val digest = MessageDigest.getInstance("SHA-256")
        content.use { input ->
            DigestInputStream(input, digest).use { it.transferTo(OutputStream.nullOutputStream()) }
        }
        return HexFormat.of().formatHex(digest.digest())
    }
}

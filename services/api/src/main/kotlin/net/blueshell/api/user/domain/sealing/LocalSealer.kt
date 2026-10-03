package net.blueshell.api.user.domain.sealing

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import java.security.GeneralSecurityException
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * The stand-in for dev and test, where no Vault holds the keys. It keeps Vault's rules: a value
 * opens only under the context it was sealed with, bound as AES-GCM's associated data, and a
 * rotated key seals under its newest version while older versions keep opening. Its keys are
 * derived from the key name and version, so it protects nothing, and production seals through
 * Vault (`privacy.sealing: vault`).
 */
@Component
@ConditionalOnProperty("privacy.sealing", havingValue = "stand-in", matchIfMissing = true)
class LocalSealer : Sealer {
    private val random = SecureRandom()
    private val newest = ConcurrentHashMap<String, Int>()

    /** Adds a version to [key], as rotating a Transit key does, and answers it. Lasts until a restart. */
    fun rotate(key: String): Int = ((newest[key] ?: FIRST) + 1).also { newest[key] = it }

    override fun seal(
        key: String,
        values: List<Sealed>,
    ): List<String> {
        val version = newest[key] ?: FIRST
        return values.map { sealed ->
            val nonce = ByteArray(NONCE).also(random::nextBytes)
            val cipher = cipher(Cipher.ENCRYPT_MODE, key, version, nonce, sealed.context)
            "$SCHEME:v$version:" + Base64.getEncoder().encodeToString(nonce + cipher.doFinal(sealed.value.toByteArray()))
        }
    }

    override fun open(
        key: String,
        values: List<Sealed>,
    ): List<String> =
        values.map { sealed ->
            val bytes = Base64.getDecoder().decode(sealed.value.substringAfterLast(':'))
            try {
                val cipher = cipher(Cipher.DECRYPT_MODE, key, keyVersionOf(sealed.value), bytes.copyOfRange(0, NONCE), sealed.context)
                String(cipher.doFinal(bytes.copyOfRange(NONCE, bytes.size)))
            } catch (refused: GeneralSecurityException) {
                throw SealedValueUnopenable(refused)
            }
        }

    override fun rewrap(
        key: String,
        values: List<Sealed>,
    ): List<String?> =
        values.map { sealed ->
            try {
                seal(key, listOf(Sealed(open(key, listOf(sealed)).single(), sealed.context))).single()
            } catch (_: SealedValueUnopenable) {
                null
            }
        }

    private fun cipher(
        mode: Int,
        key: String,
        version: Int,
        nonce: ByteArray,
        context: String,
    ): Cipher {
        val secret = SecretKeySpec(MessageDigest.getInstance("SHA-256").digest("stand-in:$key:v$version".toByteArray()), "AES")
        return Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(mode, secret, GCMParameterSpec(TAG_BITS, nonce))
            updateAAD(context.toByteArray())
        }
    }

    private companion object {
        const val SCHEME = "local"
        const val FIRST = 1
        const val NONCE = 12
        const val TAG_BITS = 128
    }
}

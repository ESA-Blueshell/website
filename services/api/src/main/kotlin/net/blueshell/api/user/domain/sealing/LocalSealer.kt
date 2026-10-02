package net.blueshell.api.user.domain.sealing

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import java.security.GeneralSecurityException
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * The stand-in for dev and test, where no Vault holds the keys. It keeps Vault's rule, a value
 * opens only under the context it was sealed with, by binding the context as AES-GCM's associated
 * data. Its key is derived from the key name, so it protects nothing, and production seals
 * through Vault (`privacy.sealing: vault`).
 */
@Component
@ConditionalOnProperty("privacy.sealing", havingValue = "stand-in", matchIfMissing = true)
class LocalSealer : Sealer {
    private val random = SecureRandom()

    override fun seal(
        key: String,
        values: List<Sealed>,
    ): List<String> =
        values.map { sealed ->
            val nonce = ByteArray(NONCE).also(random::nextBytes)
            val cipher = cipher(Cipher.ENCRYPT_MODE, key, nonce, sealed.context)
            PREFIX + Base64.getEncoder().encodeToString(nonce + cipher.doFinal(sealed.value.toByteArray()))
        }

    override fun open(
        key: String,
        values: List<Sealed>,
    ): List<String> =
        values.map { sealed ->
            val bytes = Base64.getDecoder().decode(sealed.value.removePrefix(PREFIX))
            try {
                val cipher = cipher(Cipher.DECRYPT_MODE, key, bytes.copyOfRange(0, NONCE), sealed.context)
                String(cipher.doFinal(bytes.copyOfRange(NONCE, bytes.size)))
            } catch (refused: GeneralSecurityException) {
                throw SealedValueUnopenable(refused)
            }
        }

    private fun cipher(
        mode: Int,
        key: String,
        nonce: ByteArray,
        context: String,
    ): Cipher {
        val secret = SecretKeySpec(MessageDigest.getInstance("SHA-256").digest("stand-in:$key".toByteArray()), "AES")
        return Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(mode, secret, GCMParameterSpec(TAG_BITS, nonce))
            updateAAD(context.toByteArray())
        }
    }

    private companion object {
        const val PREFIX = "local:v1:"
        const val NONCE = 12
        const val TAG_BITS = 128
    }
}

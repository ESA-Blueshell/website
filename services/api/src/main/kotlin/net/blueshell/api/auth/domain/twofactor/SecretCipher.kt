package net.blueshell.api.auth.domain.twofactor

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.security.GeneralSecurityException
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/** A secret as it is stored: which key sealed it, and the nonce and ciphertext it sealed. */
data class SealedSecret(
    val keyId: String,
    val ciphertext: String,
)

/**
 * AES-256-GCM under an application key from Vault KV (api ADR-031). The current key seals; the
 * current and every retired key open, so rotating the key is re-sealing rather than an outage.
 * `retired` is `id:base64key` pairs separated by commas. [rekey] takes a rotated key while the
 * api runs (api ADR-033).
 */
@Component
class SecretCipher(
    @Value($$"${app.two-factor.key-id}") currentKeyId: String,
    @Value($$"${app.two-factor.key}") currentKey: String,
    @Value($$"${app.two-factor.retired-keys:}") retired: String,
) {
    private val random = SecureRandom()

    @Volatile private var keyring: Keyring = Keyring.of(currentKeyId, currentKey, retired)

    /** Swaps in a new key set; a malformed one throws and leaves the keys in use. */
    fun rekey(
        currentKeyId: String,
        currentKey: String,
        retired: String,
    ) {
        keyring = Keyring.of(currentKeyId, currentKey, retired)
    }

    fun seal(secret: ByteArray): SealedSecret {
        val ring = keyring
        val nonce = ByteArray(NONCE_BYTES).also(random::nextBytes)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, ring.keys.getValue(ring.currentId), GCMParameterSpec(TAG_BITS, nonce))
        cipher.updateAAD(ring.currentId.toByteArray())
        return SealedSecret(ring.currentId, Base64.getEncoder().encodeToString(nonce + cipher.doFinal(secret)))
    }

    fun open(sealed: SealedSecret): ByteArray {
        val key = keyring.keys[sealed.keyId] ?: error("No key ${sealed.keyId} to open a two-factor secret with")
        val bytes = Base64.getDecoder().decode(sealed.ciphertext)
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, bytes, 0, NONCE_BYTES))
            cipher.updateAAD(sealed.keyId.toByteArray())
            cipher.doFinal(bytes, NONCE_BYTES, bytes.size - NONCE_BYTES)
        } catch (e: GeneralSecurityException) {
            throw IllegalStateException("A two-factor secret failed to open", e)
        }
    }

    /** The sealing key's id and every key that opens, swapped as one. */
    private class Keyring(
        val currentId: String,
        val keys: Map<String, SecretKeySpec>,
    ) {
        companion object {
            fun of(
                currentId: String,
                current: String,
                retired: String,
            ) = Keyring(
                currentId,
                buildMap {
                    retired.split(",").map { it.trim() }.filter { it.isNotEmpty() }.forEach { pair ->
                        val (id, key) = pair.split(":", limit = 2).also { require(it.size == 2) { "A retired key is id:key" } }
                        put(id, keyOf(key))
                    }
                    put(currentId, keyOf(current))
                },
            )
        }
    }

    private companion object {
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val NONCE_BYTES = 12
        const val TAG_BITS = 128
        const val KEY_BYTES = 32

        fun keyOf(base64: String): SecretKeySpec {
            val bytes = Base64.getDecoder().decode(base64.trim())
            require(bytes.size == KEY_BYTES) { "A two-factor key is $KEY_BYTES bytes" }
            return SecretKeySpec(bytes, "AES")
        }
    }
}

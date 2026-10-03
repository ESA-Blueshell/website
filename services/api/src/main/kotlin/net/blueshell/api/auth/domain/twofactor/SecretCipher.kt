package net.blueshell.api.auth.domain.twofactor

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

/** A secret as it is stored: which key sealed it, and the nonce and ciphertext it sealed. */
data class SealedSecret(
    val keyId: String,
    val ciphertext: String,
)

/**
 * Seals two-factor secrets under the two-factor keys from Vault KV (api ADR-031). [rekey] takes
 * a rotated key while the api runs (api ADR-033).
 */
@Component
class SecretCipher(
    @Value($$"${app.two-factor.key-id}") currentKeyId: String,
    @Value($$"${app.two-factor.key}") currentKey: String,
    @Value($$"${app.two-factor.retired-keys:}") retired: String,
) {
    private val keys = SealingKeys("two-factor secret", currentKeyId, currentKey, retired)

    /** Swaps in a new key set; a malformed one throws and leaves the keys in use. */
    fun rekey(
        currentKeyId: String,
        currentKey: String,
        retired: String,
    ) = keys.rekey(currentKeyId, currentKey, retired)

    fun seal(secret: ByteArray): SealedSecret = keys.seal(secret).let { SealedSecret(it.keyId, it.ciphertext) }

    fun open(sealed: SealedSecret): ByteArray = keys.open(Sealed(sealed.keyId, sealed.ciphertext))
}

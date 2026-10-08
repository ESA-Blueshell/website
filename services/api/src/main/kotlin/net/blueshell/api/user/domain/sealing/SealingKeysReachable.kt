package net.blueshell.api.user.domain.sealing

import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component

/**
 * Refuses to start where Vault seals but its key cannot be used. Without this the api would come
 * up and refuse every address later, one request at a time.
 */
@Component
@ConditionalOnProperty("privacy.sealing", havingValue = "vault")
class SealingKeysReachable(
    sealer: Sealer,
    @Value($$"${privacy.address-key:api-address}") addressKey: String,
) {
    init {
        try {
            sealer.seal(addressKey, listOf(Sealed("reachable", "startup:0")))
        } catch (away: SealingUnavailable) {
            throw IllegalStateException("The api cannot seal with Vault's Transit key $addressKey; it will not start without it", away)
        }
    }
}

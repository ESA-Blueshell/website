package net.blueshell.api.domain.auth.domain.model

import net.blueshell.api.shared.enums.ResetType
import java.time.Instant

/**
 * Domain model encapsulating recovery token validation business rules.
 */
data class RecoveryTokenValidation(
    val selector: String,
    val verifier: String,
    val expectedType: ResetType
) {
    init {
        require(selector.isNotBlank()) { "Selector cannot be blank" }
        require(verifier.isNotBlank()) { "Verifier cannot be blank" }
    }

    companion object {
        /**
         * Parse a raw token string into validation components.
         * Format: "selector.verifier"
         */
        fun fromRawToken(rawToken: String, expectedType: ResetType): RecoveryTokenValidation {
            val parts = rawToken.split(".", limit = 2)
            require(parts.size == 2) { "Invalid token format: must contain selector and verifier" }
            require(parts[0].isNotBlank()) { "Selector cannot be blank" }
            require(parts[1].isNotBlank()) { "Verifier cannot be blank" }

            return RecoveryTokenValidation(
                selector = parts[0],
                verifier = parts[1],
                expectedType = expectedType
            )
        }
    }
}

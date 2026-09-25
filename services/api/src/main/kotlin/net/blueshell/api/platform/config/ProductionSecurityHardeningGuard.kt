package net.blueshell.api.platform.config

import io.jsonwebtoken.io.Decoders
import jakarta.annotation.PostConstruct
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component

// HS512 keys shorter than the hash are rejected by the JWT library outright.
private const val HS512_MIN_KEY_BYTES = 64

private const val TWO_FACTOR_KEY_BYTES = 32

/**
 * Fail-fast guardrails for production-like environments.
 * Prevents booting with weak JWT secrets or insecure exposure toggles.
 */
@Component
@Profile("!test && !dev")
class ProductionSecurityHardeningGuard(
    @param:Value("\${app.jwt.secret:}") private val jwtSecret: String,
    @param:Value("\${app.security.require-https:true}") private val requireHttps: Boolean,
    @param:Value("\${security.openapi.public.enabled:false}") private val openApiPublicEnabled: Boolean,
    @param:Value("\${app.two-factor.key:}") private val twoFactorKey: String = "",
) {
    @PostConstruct
    fun validate() {
        require(requireHttps) {
            "app.security.require-https must be true outside dev/test profiles"
        }
        require(!openApiPublicEnabled) {
            "security.openapi.public.enabled must be false outside dev/test profiles"
        }
        require(jwtSecret.isNotBlank()) {
            "app.jwt.secret must be configured"
        }

        val decoded =
            try {
                Decoders.BASE64.decode(jwtSecret)
            } catch (ex: Exception) {
                throw IllegalStateException("app.jwt.secret must be Base64 encoded", ex)
            }

        require(decoded.size >= HS512_MIN_KEY_BYTES) {
            "app.jwt.secret must decode to at least $HS512_MIN_KEY_BYTES bytes for HS512"
        }

        val twoFactorKeyBytes = runCatching { Decoders.BASE64.decode(twoFactorKey) }.getOrNull()
        require(twoFactorKeyBytes?.size == TWO_FACTOR_KEY_BYTES) {
            "app.two-factor.key (TWO_FACTOR_ENCRYPTION_KEY) must be Base64 of $TWO_FACTOR_KEY_BYTES bytes"
        }
    }
}

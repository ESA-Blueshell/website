package net.blueshell.api.security

import io.jsonwebtoken.JwtException
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.io.Decoders
import io.jsonwebtoken.security.Keys
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.Instant
import java.util.Date
import javax.crypto.SecretKey

/**
 * The auth cookie's JWT: a signed view of a sign-in, meaningless without the record it names.
 * [rekey] takes a rotated secret while the api runs, and a token the previous secret signed
 * still reads, so a rotation signs nobody out (api ADR-033).
 */
@Component
class JwtTokenUtil(
    @Value($$"${app.jwt.secret}") secret: String,
    @param:Value($$"${app.jwt.issuer}") private val issuer: String,
    @param:Value($$"${app.jwt.audience}") private val audience: String,
    private val clock: Clock,
) {
    data class Claims(
        val subject: String,
        val sid: String,
        val jti: String,
    )

    fun mint(
        subject: String,
        sid: String,
        jti: String,
        expiresAt: Instant,
    ): String =
        Jwts
            .builder()
            .subject(subject)
            .issuer(issuer)
            .audience()
            .add(audience)
            .and()
            .id(jti)
            .claim(SID, sid)
            .issuedAt(Date.from(clock.instant()))
            .expiration(Date.from(expiresAt))
            .signWith(keys.current, Jwts.SIG.HS512)
            .compact()

    /** The claims of a valid, unexpired token of ours, or null for anything else. */
    @Suppress(
        // A null for each thing a token can lack, which reads straighter than one long condition.
        "ReturnCount",
    )
    fun read(token: String?): Claims? {
        if (token.isNullOrBlank()) return null
        val ring = keys
        val claims = parse(token, ring.current) ?: ring.previous?.let { parse(token, it) } ?: return null
        return Claims(
            subject = claims.subject?.takeIf { it.isNotBlank() } ?: return null,
            sid = (claims[SID] as? String)?.takeIf { it.isNotBlank() } ?: return null,
            jti = claims.id?.takeIf { it.isNotBlank() } ?: return null,
        )
    }

    /** Signs with a new secret and keeps the one before it for reading; a malformed one throws. */
    fun rekey(secret: String) {
        keys = SigningKeys(keyOf(secret), keys.current)
    }

    private fun parse(
        token: String,
        key: SecretKey,
    ): io.jsonwebtoken.Claims? =
        try {
            Jwts
                .parser()
                .verifyWith(key)
                .clock { Date.from(clock.instant()) }
                .requireIssuer(issuer)
                .requireAudience(audience)
                .build()
                .parseSignedClaims(token)
                .payload
        } catch (_: JwtException) {
            null
        }

    // The secret it signs with and the one before, swapped as one.
    private class SigningKeys(
        val current: SecretKey,
        val previous: SecretKey?,
    )

    @Volatile private var keys = SigningKeys(keyOf(secret), null)

    private companion object {
        const val SID = "sid"
        const val MIN_KEY_BYTES = 64

        fun keyOf(secret: String): SecretKey {
            val bytes = Decoders.BASE64.decode(secret)
            require(bytes.size >= MIN_KEY_BYTES) { "A JWT secret decodes to at least $MIN_KEY_BYTES bytes" }
            return Keys.hmacShaKeyFor(bytes)
        }
    }
}

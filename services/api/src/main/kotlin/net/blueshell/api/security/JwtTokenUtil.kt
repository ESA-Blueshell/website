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

/** The auth cookie's JWT: a signed view of a sign-in, meaningless without the record it names. */
@Component
class JwtTokenUtil(
    @param:Value($$"${app.jwt.secret}") private val secret: String,
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
            .audience().add(audience).and()
            .id(jti)
            .claim(SID, sid)
            .issuedAt(Date.from(clock.instant()))
            .expiration(Date.from(expiresAt))
            .signWith(signingKey, Jwts.SIG.HS512)
            .compact()

    /** The claims of a valid, unexpired token of ours, or null for anything else. */
    // A null for each thing a token can lack, which reads straighter than one long condition.
    @Suppress("ReturnCount")
    fun read(token: String?): Claims? {
        if (token.isNullOrBlank()) return null
        val claims =
            try {
                Jwts
                    .parser()
                    .verifyWith(signingKey)
                    .clock { Date.from(clock.instant()) }
                    .requireIssuer(issuer)
                    .requireAudience(audience)
                    .build()
                    .parseSignedClaims(token)
                    .payload
            } catch (_: JwtException) {
                return null
            }
        return Claims(
            subject = claims.subject?.takeIf { it.isNotBlank() } ?: return null,
            sid = (claims[SID] as? String)?.takeIf { it.isNotBlank() } ?: return null,
            jti = claims.id?.takeIf { it.isNotBlank() } ?: return null,
        )
    }

    private val signingKey: SecretKey by lazy { Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret)) }

    private companion object {
        const val SID = "sid"
    }
}

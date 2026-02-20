package net.blueshell.api.infrastructure.security

import io.jsonwebtoken.Claims
import io.jsonwebtoken.ExpiredJwtException
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.io.Decoders
import io.jsonwebtoken.security.Keys
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.util.*
import java.util.function.Function
import javax.crypto.SecretKey

@Component("commonJwtTokenUtil")
class JwtTokenUtil(
    @param:Value($$"${app.jwt.expiration}") private val expiration: Long,
    @param:Value($$"${app.jwt.secret}") private val secret: String,
    @param:Value($$"${app.jwt.issuer}") private val issuer: String,
    @param:Value($$"${app.jwt.audience}") private val audience: String
) {
    data class JwtValidationResult(
        val username: String?,
        val jti: String?,
        val expired: Boolean,
        val error: Exception?
    ) {
        val isValid: Boolean
            get() = error == null && !expired && username != null && !jti.isNullOrBlank()
    }

    fun getUsernameFromToken(token: String?): String {
        return getClaimFromToken(token) { obj: Claims? -> obj?.subject }!!
    }

    fun getExpirationDateFromToken(token: String?): Date {
        return getClaimFromToken(token) { obj: Claims? -> obj?.expiration }!!
    }

    fun <T> getClaimFromToken(token: String?, claimsResolver: Function<Claims?, T?>): T? {
        val claims = getAllClaimsFromToken(token)
        return claimsResolver.apply(claims)
    }

    private fun getAllClaimsFromToken(token: String?): Claims? {
        return Jwts.parser()
            .verifyWith(this.signingKey) // new: verifyWith(SecretKey)
            .build()
            .parseSignedClaims(token)
            .payload
    }

    fun generateToken(username: String): String {
        val claims: MutableMap<String, Any> = HashMap<String, Any>()
        claims["aud"] = audience
        return doGenerateToken(claims, username)
    }

    private fun doGenerateToken(claims: MutableMap<String, Any>, subject: String): String {
        return Jwts.builder()
            .claims(claims)
            .subject(subject)
            .issuer(issuer)
            .id(UUID.randomUUID().toString())
            .issuedAt(Date())
            .expiration(Date(System.currentTimeMillis() + expiration))
            .signWith(this.signingKey, Jwts.SIG.HS512)
            .compact()
    }

    fun parseAndValidate(token: String?): JwtValidationResult {
        if (token.isNullOrBlank()) {
            return JwtValidationResult(null, null, expired = false, error = IllegalArgumentException("Token is blank"))
        }
        return try {
            val claims = getAllClaimsFromToken(token)
            val claimsValidationError = validateClaims(claims)
            if (claimsValidationError != null) {
                return JwtValidationResult(claims?.subject, claims?.id, expired = false, error = claimsValidationError)
            }
            val expired = claims?.expiration?.before(Date()) == true
            JwtValidationResult(claims?.subject, claims?.id, expired, null)
        } catch (e: ExpiredJwtException) {
            val claims = e.claims
            val claimsValidationError = validateClaims(claims)
            if (claimsValidationError != null) {
                return JwtValidationResult(claims?.subject, claims?.id, expired = true, error = claimsValidationError)
            }
            JwtValidationResult(claims?.subject, claims?.id, expired = true, error = e)
        } catch (e: Exception) {
            JwtValidationResult(null, null, expired = false, error = e)
        }
    }

    fun isTokenValid(token: String?): Boolean {
        return parseAndValidate(token).isValid
    }

    private fun validateClaims(claims: Claims?): Exception? {
        if (claims == null) {
            return IllegalArgumentException("Token has no claims")
        }
        if (claims.subject.isNullOrBlank()) {
            return IllegalArgumentException("Token subject is missing")
        }
        if (claims.id.isNullOrBlank()) {
            return IllegalArgumentException("Token jti is missing")
        }
        if (claims.issuer != issuer) {
            return IllegalArgumentException("Token issuer is invalid")
        }

        val audienceClaim = claims["aud"]
        val audiences = when (audienceClaim) {
            is String -> setOf(audienceClaim)
            is Collection<*> -> audienceClaim.mapNotNull { it as? String }.toSet()
            else -> emptySet()
        }
        if (!audiences.contains(audience)) {
            return IllegalArgumentException("Token audience is invalid")
        }

        return null
    }

    private val signingKey: SecretKey by lazy {
        val keyBytes = Decoders.BASE64.decode(secret)
        Keys.hmacShaKeyFor(keyBytes)
    }
}

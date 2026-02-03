package net.blueshell.api.auth

import io.jsonwebtoken.Claims
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.io.Decoders
import io.jsonwebtoken.security.Keys
import org.springframework.beans.factory.annotation.Value
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.stereotype.Component
import java.util.*
import java.util.function.Function
import javax.crypto.SecretKey

@Component("commonJwtTokenUtil")
class JwtTokenUtil {
    @Value("\${app.jwt.expiration}")
    private val expiration: Long? = null

    @Value("\${app.jwt.secret}")
    private val secret: String? = null

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

    fun isTokenExpired(token: String?): Boolean {
        val tokenExpiration = getExpirationDateFromToken(token)
        return tokenExpiration.before(Date())
    }

    fun generateToken(userDetails: UserDetails): String? {
        val claims: MutableMap<String?, Any?> = HashMap<String?, Any?>()
        return doGenerateToken(claims, userDetails.username)
    }

    private fun doGenerateToken(claims: MutableMap<String?, Any?>?, subject: String?): String? {
        return Jwts.builder()
            .claims(claims)
            .subject(subject)
            .issuedAt(Date())
            .expiration(Date(System.currentTimeMillis() + expiration!!))
            .signWith(this.signingKey, Jwts.SIG.HS512)
            .compact()
    }

    fun isTokenValid(token: String?): Boolean {
        try {
            getAllClaimsFromToken(token)
            return true
        } catch (e: Exception) {
            return false
        }
    }

    fun validateToken(token: String?, userDetails: UserDetails): Boolean {
        val username = getUsernameFromToken(token)
        return username == userDetails.username && !isTokenExpired(token)
    }

    private val signingKey: SecretKey
        get() {
            val keyBytes = Decoders.BASE64.decode(secret)
            return Keys.hmacShaKeyFor(keyBytes)
        }
}

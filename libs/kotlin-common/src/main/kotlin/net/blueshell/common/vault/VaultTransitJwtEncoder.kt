package net.blueshell.common.vault

import org.springframework.security.oauth2.jose.jws.JwsAlgorithm
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.jwt.JwtEncoder
import org.springframework.security.oauth2.jwt.JwtEncoderParameters
import org.springframework.security.oauth2.jwt.JwtEncodingException
import java.time.Instant
import java.util.Base64

/**
 * Signs JWTs via Vault Transit (RS256 / PKCS1v15 + SHA-256).
 * The key must be an RSA-2048 or RSA-4096 key created in Vault's transit engine.
 */
class VaultTransitJwtEncoder(
    private val client: VaultTransitClient,
    private val keyName: String,
) : JwtEncoder {

    private val base64url = Base64.getUrlEncoder().withoutPadding()
    private val base64 = Base64.getDecoder()

    override fun encode(parameters: JwtEncoderParameters): Jwt {
        val headers = parameters.jwsHeader
        val claims = parameters.claims

        // Fetch the current key version and use the latest.
        val publicKeys = try {
            client.readPublicKeys(keyName)
        } catch (ex: Exception) {
            throw JwtEncodingException("Failed to read Vault transit key '$keyName'", ex)
        }

        val latest = publicKeys.maxByOrNull { it.keyVersion }
            ?: throw JwtEncodingException("No key versions found for '$keyName'")

        val kid = "$keyName:v${latest.keyVersion}"

        val alg: JwsAlgorithm = headers?.algorithm ?: SignatureAlgorithm.RS256
        val headerJson = buildHeaderJson(kid, alg)
        val claimsJson = buildClaimsJson(claims.claims)

        val headerEncoded = base64url.encodeToString(headerJson.toByteArray(Charsets.UTF_8))
        val claimsEncoded = base64url.encodeToString(claimsJson.toByteArray(Charsets.UTF_8))
        val signingInput = "$headerEncoded.$claimsEncoded"

        val inputB64 = Base64.getEncoder().encodeToString(signingInput.toByteArray(Charsets.UTF_8))

        val signatureB64Vault = try {
            client.sign(keyName, inputB64)
        } catch (ex: Exception) {
            throw JwtEncodingException("Vault transit sign failed for key '$keyName'", ex)
        }

        // Vault returns standard base64; JWT requires base64url without padding.
        val signatureBytes = base64.decode(signatureB64Vault)
        val signatureEncoded = base64url.encodeToString(signatureBytes)

        val tokenValue = "$signingInput.$signatureEncoded"

        val claimsMap = claims.claims
        val issuedAt = (claimsMap["iat"] as? Instant) ?: Instant.now()
        val expiresAt = (claimsMap["exp"] as? Instant) ?: issuedAt.plusSeconds(900)

        return Jwt(tokenValue, issuedAt, expiresAt, mapOf("alg" to "RS256", "kid" to kid), claimsMap)
    }

    private fun buildHeaderJson(kid: String, alg: JwsAlgorithm): String {
        return """{"alg":"${alg.name}","typ":"JWT","kid":"$kid"}"""
    }

    private fun buildClaimsJson(claims: Map<String, Any?>): String {
        val sb = StringBuilder("{")
        claims.entries.forEachIndexed { index, (key, value) ->
            if (index > 0) sb.append(",")
            sb.append('"').append(key).append('"').append(':')
            sb.append(toJsonValue(value))
        }
        sb.append("}")
        return sb.toString()
    }

    private fun toJsonValue(value: Any?): String = when (value) {
        null -> "null"
        is String -> "\"${value.replace("\"", "\\\"")}\""
        is Number -> value.toString()
        is Boolean -> value.toString()
        is Instant -> value.epochSecond.toString()
        is List<*> -> "[${value.joinToString(",") { toJsonValue(it) }}]"
        is Map<*, *> -> "{${value.entries.joinToString(",") { (k, v) -> "\"$k\":${toJsonValue(v)}" }}}"
        else -> "\"${value.toString().replace("\"", "\\\"")}\""
    }
}

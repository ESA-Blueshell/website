package net.blueshell.api.system.oidc

import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/**
 * Lightweight helpers for OIDC system tests. Mirrors the parts of
 * personal-stack's `TestHelper` that are portable: PKCE S256, JWT
 * payload decode, JSON parsing.
 */
object OidcTestHelper {

    private val mapper = ObjectMapper()
    private val urlEncoder = Base64.getUrlEncoder().withoutPadding()
    private val urlDecoder = Base64.getUrlDecoder()
    private val random = SecureRandom()

    data class Pkce(val verifier: String, val challenge: String) {
        val method: String = "S256"
    }

    fun newPkce(): Pkce {
        val bytes = ByteArray(32)
        random.nextBytes(bytes)
        val verifier = urlEncoder.encodeToString(bytes)
        val sha = MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII))
        val challenge = urlEncoder.encodeToString(sha)
        return Pkce(verifier, challenge)
    }

    /**
     * Decode a JWT payload (middle segment) without signature verification.
     * Tests assert claim shape, not cryptographic validity — the SAS
     * encoder produces signed tokens, but verifying signatures here would
     * just re-test the JOSE library.
     */
    fun decodePayload(jwt: String): JsonNode {
        val segments = jwt.split('.')
        require(segments.size == 3) { "Not a JWT: $jwt" }
        val json = String(urlDecoder.decode(segments[1]), Charsets.UTF_8)
        return mapper.readTree(json)
    }

    fun parseJson(body: String): JsonNode = mapper.readTree(body)

    /**
     * Iterate the elements of an array-valued JsonNode and project each
     * to a string. Jackson 3.1's `JsonNode.map` is Optional-style
     * (applies to the whole node) and shadows Kotlin's `Iterable.map`.
     */
    fun stringValues(node: JsonNode?): List<String> =
        node?.iterator()?.asSequence()?.map { it.asString() }?.toList().orEmpty()

    /**
     * Iterate the elements of an array-valued JsonNode and project via
     * a custom mapper. Same shadowing caveat as `stringValues`.
     */
    fun <R> mapElements(node: JsonNode?, transform: (JsonNode) -> R): List<R> =
        node?.iterator()?.asSequence()?.map(transform)?.toList().orEmpty()

    /**
     * Form-urlencode a parameter map (for /oauth2/token POSTs).
     */
    fun formEncode(params: Map<String, String>): String =
        params.entries.joinToString("&") { (k, v) ->
            "${java.net.URLEncoder.encode(k, Charsets.UTF_8)}=${java.net.URLEncoder.encode(v, Charsets.UTF_8)}"
        }

    /**
     * Pull a single query param from a URL string. Returns null if the
     * param is not present.
     */
    fun queryParam(url: String, name: String): String? {
        val q = url.substringAfter('?', "")
        if (q.isEmpty()) return null
        return q.split('&')
            .map { it.split('=', limit = 2) }
            .firstOrNull { it.firstOrNull() == name }
            ?.getOrNull(1)
            ?.let { java.net.URLDecoder.decode(it, Charsets.UTF_8) }
    }
}

package net.blueshell.api.security

import jakarta.servlet.http.HttpServletRequest
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.security.web.util.matcher.IpAddressMatcher
import org.springframework.stereotype.Component

// Room for brackets and a port around the longest literal below.
private const val MAX_RAW_IP_LITERAL_LENGTH = 64

// An IPv4-mapped IPv6 address, the longest form there is.
private const val MAX_IP_LITERAL_LENGTH = 45

private val IP_LITERAL_PATTERN = Regex("^[0-9A-Fa-f:.]+$")

/**
 * The address a request really came from. The forwarding headers are believed only when the
 * connection itself comes from a trusted proxy, so a caller cannot name an address of its choosing.
 */
@Component
class ClientAddresses(
    @Value("\${security.auth-rate-limit.trusted-proxy-cidrs:$DEFAULT_TRUSTED_PROXY_CIDRS}")
    trustedProxyCidrs: String = DEFAULT_TRUSTED_PROXY_CIDRS,
) {
    private val trustedProxyMatchers =
        trustedProxyCidrs
            .split(",")
            .mapNotNull { raw ->
                val cidr = raw.trim()
                if (cidr.isBlank()) {
                    return@mapNotNull null
                }
                runCatching { IpAddressMatcher(cidr) }
                    .onFailure { log.warn("Ignoring invalid trusted proxy CIDR '{}'", cidr) }
                    .getOrNull()
            }

    fun resolve(request: HttpServletRequest): String {
        val remoteAddr = normalizeIpLiteral(request.remoteAddr) ?: "unknown"
        if (!isTrustedProxy(remoteAddr)) {
            return remoteAddr
        }

        normalizeIpLiteral(request.getHeader("X-Real-IP"))?.let { return it }

        request
            .getHeader("X-Forwarded-For")
            ?.split(",")
            ?.asSequence()
            ?.mapNotNull { normalizeIpLiteral(it) }
            ?.firstOrNull()
            ?.let { return it }

        return remoteAddr
    }

    private fun isTrustedProxy(remoteAddr: String): Boolean {
        if (remoteAddr == "unknown") {
            return false
        }
        return trustedProxyMatchers.any { matcher ->
            runCatching { matcher.matches(remoteAddr) }.getOrDefault(false)
        }
    }

    private fun normalizeIpLiteral(raw: String?): String? {
        val value = raw?.trim()?.takeIf { it.isNotBlank() } ?: return null
        if (value.length > MAX_RAW_IP_LITERAL_LENGTH) {
            return null
        }

        val unbracketed =
            if (value.startsWith("[") && value.contains("]")) {
                value.substringAfter('[').substringBefore(']')
            } else {
                value
            }

        val withoutPort =
            if (unbracketed.contains('.') && unbracketed.count { it == ':' } == 1) {
                unbracketed.substringBefore(':')
            } else {
                unbracketed
            }.trim()

        if (withoutPort.isBlank() || withoutPort.length > MAX_IP_LITERAL_LENGTH) {
            return null
        }
        if (!IP_LITERAL_PATTERN.matches(withoutPort)) {
            return null
        }
        return withoutPort.lowercase()
    }

    private companion object {
        val log = LoggerFactory.getLogger(ClientAddresses::class.java)
    }
}

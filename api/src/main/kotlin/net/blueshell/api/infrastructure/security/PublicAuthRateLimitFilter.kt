package net.blueshell.api.infrastructure.security

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.security.web.util.matcher.IpAddressMatcher
import org.springframework.stereotype.Component
import org.springframework.util.AntPathMatcher
import org.springframework.web.filter.OncePerRequestFilter
import java.time.Duration

private const val DEFAULT_TRUSTED_PROXY_CIDRS = "127.0.0.1/32,::1/128,10.0.0.0/8,172.16.0.0/12,192.168.0.0/16,fc00::/7"
private val IP_LITERAL_PATTERN = Regex("^[0-9A-Fa-f:.]+$")

@Component
@ConditionalOnProperty(
    name = ["security.auth-rate-limit.enabled"],
    havingValue = "true",
    matchIfMissing = true
)
class PublicAuthRateLimitFilter(
    private val limiter: InMemoryRequestRateLimiter,
    @Value("\${security.auth-rate-limit.trusted-proxy-cidrs:$DEFAULT_TRUSTED_PROXY_CIDRS}")
    trustedProxyCidrs: String = DEFAULT_TRUSTED_PROXY_CIDRS
) : OncePerRequestFilter() {

    private val log = LoggerFactory.getLogger(javaClass)

    private data class Rule(
        val method: String,
        val pathPattern: String,
        val maxRequests: Int,
        val window: Duration
    )

    private val pathMatcher = AntPathMatcher()
    private val trustedProxyMatchers = trustedProxyCidrs
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

    private val rules = listOf(
        Rule(HttpMethod.POST.name(), "/auth", maxRequests = 10, window = Duration.ofMinutes(1)),
        Rule(HttpMethod.POST.name(), "/users", maxRequests = 10, window = Duration.ofMinutes(1)),
        Rule(HttpMethod.POST.name(), "/recovery/password", maxRequests = 10, window = Duration.ofMinutes(10)),
        Rule(HttpMethod.POST.name(), "/recovery/user/activate", maxRequests = 10, window = Duration.ofMinutes(10)),
        Rule(HttpMethod.POST.name(), "/recovery/member/activate", maxRequests = 10, window = Duration.ofMinutes(10)),
        Rule(HttpMethod.POST.name(), "/recovery/password/reset/*", maxRequests = 5, window = Duration.ofMinutes(10)),
        Rule(HttpMethod.POST.name(), "/recovery/user/activate/resend/*", maxRequests = 5, window = Duration.ofMinutes(10)),
    )

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val servletPath = request.servletPath
        val rule = rules.firstOrNull { it.method == request.method && pathMatcher.match(it.pathPattern, servletPath) }

        if (rule == null) {
            filterChain.doFilter(request, response)
            return
        }

        val key = buildRateLimitKey(rule, request)
        val decision = limiter.tryAcquire(key, rule.maxRequests, rule.window)
        if (decision.allowed) {
            filterChain.doFilter(request, response)
            return
        }

        response.status = HttpStatus.TOO_MANY_REQUESTS.value()
        response.contentType = MediaType.APPLICATION_PROBLEM_JSON_VALUE
        response.characterEncoding = Charsets.UTF_8.name()
        response.setHeader("Retry-After", decision.retryAfterSeconds.toString())
        response.writer.write(
            """
            {"type":"about:blank","title":"Too Many Requests","status":429,"detail":"Too many requests. Please try again later."}
            """.trimIndent()
        )
    }

    private fun buildRateLimitKey(rule: Rule, request: HttpServletRequest): String {
        val client = resolveClientIp(request)
        return "${rule.method}|${rule.pathPattern}|$client"
    }

    private fun resolveClientIp(request: HttpServletRequest): String {
        val remoteAddr = normalizeIpLiteral(request.remoteAddr) ?: "unknown"
        if (!isTrustedProxy(remoteAddr)) {
            return remoteAddr
        }

        normalizeIpLiteral(request.getHeader("X-Real-IP"))?.let { return it }

        request.getHeader("X-Forwarded-For")
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
        if (value.length > 64) {
            return null
        }

        val unbracketed = if (value.startsWith("[") && value.contains("]")) {
            value.substringAfter('[').substringBefore(']')
        } else {
            value
        }

        val withoutPort = if (unbracketed.contains('.') && unbracketed.count { it == ':' } == 1) {
            unbracketed.substringBefore(':')
        } else {
            unbracketed
        }.trim()

        if (withoutPort.isBlank() || withoutPort.length > 45) {
            return null
        }
        if (!IP_LITERAL_PATTERN.matches(withoutPort)) {
            return null
        }
        return withoutPort.lowercase()
    }
}

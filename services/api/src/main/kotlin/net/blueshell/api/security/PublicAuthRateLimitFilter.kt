package net.blueshell.api.security

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
import net.blueshell.api.shared.web.SignupHeaders
import java.security.MessageDigest
import java.time.Duration

/**
 * Requests per window one address may make to a signup step, however many applicants it
 * claims to be. High enough that a lecture hall on one NAT never reaches it, low enough
 * that inventing tokens is not a way around the per-applicant limit.
 */
private const val SHARED_ADDRESS_CEILING = 120

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

    /**
     * What a rule counts against.
     *
     * A signup step after registration carries the continuation token, and that token is
     * one applicant. Counting those steps per address instead put every applicant at an
     * intro event, a campus NAT or one household into the same bucket, so the eleventh
     * person in a minute was refused on whichever step they had reached.
     *
     * APPLICANT counts per token *as well as* per address, never instead of it. A token
     * is a header the caller chooses, so counting only by it hands out a fresh bucket for
     * every made-up value — no limit at all, on endpoints that send mail. The address
     * ceiling is the one that survives that, and is set high enough that a shared NAT
     * reaches it only under abuse.
     */
    private enum class CountedPer { CLIENT, APPLICANT }

    private data class Rule(
        val method: String,
        val pathPattern: String,
        val maxRequests: Int,
        val window: Duration,
        val countedPer: CountedPer = CountedPer.CLIENT,
        /** Requests per minute from one address, whoever they claim to be. */
        val clientCeiling: Int = SHARED_ADDRESS_CEILING
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
        Rule(HttpMethod.POST.name(), "/signup", maxRequests = 10, window = Duration.ofMinutes(1)),
        Rule(
            HttpMethod.POST.name(), "/signup/address",
            maxRequests = 10, window = Duration.ofMinutes(1), countedPer = CountedPer.APPLICANT
        ),
        Rule(
            HttpMethod.POST.name(), "/signup/apply",
            maxRequests = 10, window = Duration.ofMinutes(1), countedPer = CountedPer.APPLICANT
        ),
        Rule(
            HttpMethod.PATCH.name(), "/signup/details",
            maxRequests = 10, window = Duration.ofMinutes(1), countedPer = CountedPer.APPLICANT
        ),
        // Sends mail, so it is limited more tightly than the writes, and its address
        // ceiling is lower than theirs: twenty corrections from one address in ten
        // minutes is not a lecture hall, it is somebody spending our mail reputation.
        // Per applicant so that one person retyping cannot lock out the next one.
        Rule(
            HttpMethod.PATCH.name(), "/signup/email",
            maxRequests = 3, window = Duration.ofMinutes(10),
            countedPer = CountedPer.APPLICANT, clientCeiling = 20
        ),
        // Read back once per page load, so per applicant like the writes; the address
        // ceiling behind it is what a caller trawling for a token runs into.
        Rule(
            HttpMethod.GET.name(), "/signup/session",
            maxRequests = 20, window = Duration.ofMinutes(1), countedPer = CountedPer.APPLICANT
        ),
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

        val decision = acquire(rule, request)
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

    /**
     * Both buckets a request falls in, refusing on whichever trips first.
     *
     * The address bucket is charged first and always: it is the one a caller cannot
     * choose, so it is what bounds a caller inventing a token per request.
     */
    private fun acquire(rule: Rule, request: HttpServletRequest): InMemoryRequestRateLimiter.Decision {
        val client = resolveClientIp(request)
        val applicant = applicantKey(rule, request)
            ?: return limiter.tryAcquire(keyFor(rule, client), rule.maxRequests, rule.window)

        val byAddress = limiter.tryAcquire(keyFor(rule, "ceiling:$client"), rule.clientCeiling, rule.window)
        if (!byAddress.allowed) {
            return byAddress
        }
        return limiter.tryAcquire(keyFor(rule, applicant), rule.maxRequests, rule.window)
    }

    private fun keyFor(rule: Rule, counted: String): String = "${rule.method}|${rule.pathPattern}|$counted"

    /**
     * The applicant a signup step belongs to, or null when the request names none and
     * the address is the only thing to count. Digested rather than used raw: this ends up
     * in a map key, and the token is a live credential.
     */
    private fun applicantKey(rule: Rule, request: HttpServletRequest): String? {
        if (rule.countedPer != CountedPer.APPLICANT) {
            return null
        }
        val token = request.getHeader(SignupHeaders.SIGNUP_TOKEN)?.trim()?.takeIf { it.isNotBlank() }
            ?: return null
        val digest = MessageDigest.getInstance("SHA-256").digest(token.toByteArray(Charsets.UTF_8))
        return "applicant:" + digest.take(16).joinToString("") { "%02x".format(it) }
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

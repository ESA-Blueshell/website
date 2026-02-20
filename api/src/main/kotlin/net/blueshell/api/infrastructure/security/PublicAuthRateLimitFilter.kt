package net.blueshell.api.infrastructure.security

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.util.AntPathMatcher
import org.springframework.web.filter.OncePerRequestFilter
import java.time.Duration

@Component
@ConditionalOnProperty(
    name = ["security.auth-rate-limit.enabled"],
    havingValue = "true",
    matchIfMissing = true
)
class PublicAuthRateLimitFilter(
    private val limiter: InMemoryRequestRateLimiter
) : OncePerRequestFilter() {

    private data class Rule(
        val method: String,
        val pathPattern: String,
        val maxRequests: Int,
        val window: Duration
    )

    private val pathMatcher = AntPathMatcher()

    private val rules = listOf(
        Rule(HttpMethod.POST.name(), "/auth", maxRequests = 10, window = Duration.ofMinutes(1)),
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
        val forwardedFor = request.getHeader("X-Forwarded-For")
            ?.split(",")
            ?.firstOrNull()
            ?.trim()
            ?.takeIf { it.isNotBlank() }
        val client = forwardedFor ?: request.remoteAddr ?: "unknown"
        return "${rule.pathPattern}|$client|${request.requestURI}"
    }
}

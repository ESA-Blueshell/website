package net.blueshell.api.security

import jakarta.servlet.FilterChain
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import java.util.concurrent.atomic.AtomicInteger

class PublicAuthRateLimitFilterTest {

    @Test
    fun `rate limits protected auth endpoint`() {
        val limiter = InMemoryRequestRateLimiter(cleanupInterval = 1)
        val filter = PublicAuthRateLimitFilter(limiter)
        val chainCalls = AtomicInteger(0)

        repeat(10) {
            val response = invoke(filter, "POST", "/auth", chainCalls)
            assertThat(response.status).isEqualTo(200)
        }

        val blocked = invoke(filter, "POST", "/auth", chainCalls)

        assertThat(blocked.status).isEqualTo(429)
        assertThat(blocked.getHeader("Retry-After")).isNotBlank
        assertThat(blocked.contentAsString).contains("Too many requests")
        assertThat(chainCalls.get()).isEqualTo(10)
    }

    @Test
    fun `rate limits public user registration endpoint`() {
        val limiter = InMemoryRequestRateLimiter(cleanupInterval = 1)
        val filter = PublicAuthRateLimitFilter(limiter)
        val chainCalls = AtomicInteger(0)

        repeat(10) {
            val response = invoke(filter, "POST", "/users", chainCalls)
            assertThat(response.status).isEqualTo(200)
        }

        val blocked = invoke(filter, "POST", "/users", chainCalls)

        assertThat(blocked.status).isEqualTo(429)
        assertThat(blocked.getHeader("Retry-After")).isNotBlank
        assertThat(chainCalls.get()).isEqualTo(10)
    }

    @Test
    fun `does not rate limit unrelated endpoints`() {
        val limiter = InMemoryRequestRateLimiter(cleanupInterval = 1)
        val filter = PublicAuthRateLimitFilter(limiter)
        val chainCalls = AtomicInteger(0)

        repeat(20) {
            val response = invoke(filter, "GET", "/events", chainCalls)
            assertThat(response.status).isEqualTo(200)
        }

        assertThat(chainCalls.get()).isEqualTo(20)
    }

    @Test
    fun `ignores spoofed forwarded headers when request is not from trusted proxy`() {
        val limiter = InMemoryRequestRateLimiter(cleanupInterval = 1)
        val filter = PublicAuthRateLimitFilter(limiter)
        val chainCalls = AtomicInteger(0)

        repeat(10) {
            val response = invoke(
                filter = filter,
                method = "POST",
                path = "/auth",
                chainCalls = chainCalls,
                remoteAddr = "198.51.100.20",
                headers = mapOf("X-Forwarded-For" to "203.0.113.$it")
            )
            assertThat(response.status).isEqualTo(200)
        }

        val blocked = invoke(
            filter = filter,
            method = "POST",
            path = "/auth",
            chainCalls = chainCalls,
            remoteAddr = "198.51.100.20",
            headers = mapOf("X-Forwarded-For" to "203.0.113.250")
        )

        assertThat(blocked.status).isEqualTo(429)
        assertThat(chainCalls.get()).isEqualTo(10)
    }

    @Test
    fun `prefers x real ip from trusted proxy and does not trust changing x forwarded for`() {
        val limiter = InMemoryRequestRateLimiter(cleanupInterval = 1)
        val filter = PublicAuthRateLimitFilter(limiter)
        val chainCalls = AtomicInteger(0)

        repeat(10) {
            val response = invoke(
                filter = filter,
                method = "POST",
                path = "/auth",
                chainCalls = chainCalls,
                remoteAddr = "127.0.0.1",
                headers = mapOf(
                    "X-Real-IP" to "203.0.113.10",
                    "X-Forwarded-For" to "198.51.100.$it"
                )
            )
            assertThat(response.status).isEqualTo(200)
        }

        val blocked = invoke(
            filter = filter,
            method = "POST",
            path = "/auth",
            chainCalls = chainCalls,
            remoteAddr = "127.0.0.1",
            headers = mapOf(
                "X-Real-IP" to "203.0.113.10",
                "X-Forwarded-For" to "198.51.100.250"
            )
        )

        assertThat(blocked.status).isEqualTo(429)
        assertThat(chainCalls.get()).isEqualTo(10)
    }

    @Test
    fun `normalizes reset token path under same rate limit rule`() {
        val limiter = InMemoryRequestRateLimiter(cleanupInterval = 1)
        val filter = PublicAuthRateLimitFilter(limiter)
        val chainCalls = AtomicInteger(0)

        repeat(5) {
            val response = invoke(
                filter = filter,
                method = "POST",
                path = "/recovery/password/reset/token-$it",
                chainCalls = chainCalls
            )
            assertThat(response.status).isEqualTo(200)
        }

        val blocked = invoke(
            filter = filter,
            method = "POST",
            path = "/recovery/password/reset/token-over-limit",
            chainCalls = chainCalls
        )

        assertThat(blocked.status).isEqualTo(429)
        assertThat(chainCalls.get()).isEqualTo(5)
    }

    // Applicants at an intro event share one public address, so counting a signup step
    // per address made the eleventh person in a minute the one who got stuck. Each step
    // after registration carries the continuation token, which is one applicant.
    @Test
    fun `counts a signup step per continuation token, not per shared address`() {
        val limiter = InMemoryRequestRateLimiter(cleanupInterval = 1)
        val filter = PublicAuthRateLimitFilter(limiter)
        val chainCalls = AtomicInteger(0)

        // Thirty applicants on one campus NAT, well inside the address ceiling, each
        // taking one step. Counted per address this refused the eleventh of them.
        repeat(30) {
            val response = invoke(
                filter = filter,
                method = "POST",
                path = "/signup/address",
                chainCalls = chainCalls,
                remoteAddr = "203.0.113.9",
                headers = mapOf("X-Signup-Token" to "applicant-$it")
            )
            assertThat(response.status).isEqualTo(200)
        }

        assertThat(chainCalls.get()).isEqualTo(30)
    }

    @Test
    fun `still limits one applicant hammering a signup step`() {
        val limiter = InMemoryRequestRateLimiter(cleanupInterval = 1)
        val filter = PublicAuthRateLimitFilter(limiter)
        val chainCalls = AtomicInteger(0)

        repeat(10) {
            val response = invoke(
                filter = filter,
                method = "POST",
                path = "/signup/address",
                chainCalls = chainCalls,
                headers = mapOf("X-Signup-Token" to "one-applicant")
            )
            assertThat(response.status).isEqualTo(200)
        }

        val blocked = invoke(
            filter = filter,
            method = "POST",
            path = "/signup/address",
            chainCalls = chainCalls,
            headers = mapOf("X-Signup-Token" to "one-applicant")
        )

        assertThat(blocked.status).isEqualTo(429)
        assertThat(chainCalls.get()).isEqualTo(10)
    }

    @Test
    fun `keeps registration itself counted per address, since it carries no token`() {
        val limiter = InMemoryRequestRateLimiter(cleanupInterval = 1)
        val filter = PublicAuthRateLimitFilter(limiter)
        val chainCalls = AtomicInteger(0)

        repeat(10) {
            assertThat(invoke(filter, "POST", "/signup", chainCalls).status).isEqualTo(200)
        }

        assertThat(invoke(filter, "POST", "/signup", chainCalls).status).isEqualTo(429)
    }

    // A token is a header the caller chooses. Counting only by it would hand a fresh
    // bucket to every made-up value, which is no limit at all on endpoints that send
    // mail, so the address ceiling is charged as well and is what catches this.
    @Test
    fun `a caller inventing a token per request still runs into a limit`() {
        val limiter = InMemoryRequestRateLimiter(cleanupInterval = 1)
        val filter = PublicAuthRateLimitFilter(limiter)
        val chainCalls = AtomicInteger(0)

        var refused = 0
        repeat(400) {
            val response = invoke(
                filter = filter,
                method = "PATCH",
                path = "/signup/email",
                chainCalls = chainCalls,
                remoteAddr = "203.0.113.44",
                headers = mapOf("X-Signup-Token" to "made-up-$it")
            )
            if (response.status == 429) refused++
        }

        assertThat(refused).isGreaterThan(0)
        // The mail endpoint's ceiling is lower than the writes', so it holds sooner.
        assertThat(chainCalls.get()).isLessThanOrEqualTo(20)
    }

    @Test
    fun `a caller inventing a token cannot reach past the address ceiling`() {
        val limiter = InMemoryRequestRateLimiter(cleanupInterval = 1)
        val filter = PublicAuthRateLimitFilter(limiter)
        val chainCalls = AtomicInteger(0)

        repeat(400) {
            invoke(
                filter = filter,
                method = "POST",
                path = "/signup/address",
                chainCalls = chainCalls,
                remoteAddr = "203.0.113.45",
                headers = mapOf("X-Signup-Token" to "made-up-$it")
            )
        }

        assertThat(chainCalls.get()).isLessThanOrEqualTo(120)
    }

    @Test
    fun `reading a signup back is limited the same way`() {
        val limiter = InMemoryRequestRateLimiter(cleanupInterval = 1)
        val filter = PublicAuthRateLimitFilter(limiter)
        val chainCalls = AtomicInteger(0)

        repeat(400) {
            invoke(
                filter = filter,
                method = "GET",
                path = "/signup/session",
                chainCalls = chainCalls,
                remoteAddr = "203.0.113.46",
                headers = mapOf("X-Signup-Token" to "guess-$it")
            )
        }

        assertThat(chainCalls.get()).isLessThanOrEqualTo(120)
    }

    private fun invoke(
        filter: PublicAuthRateLimitFilter,
        method: String,
        path: String,
        chainCalls: AtomicInteger,
        remoteAddr: String = "127.0.0.1",
        headers: Map<String, String> = emptyMap(),
    ): MockHttpServletResponse {
        val request = MockHttpServletRequest(method, path).apply {
            servletPath = path
            this.remoteAddr = remoteAddr
            headers.forEach { (name, value) -> addHeader(name, value) }
        }
        val response = MockHttpServletResponse()
        val chain = FilterChain { _, _ -> chainCalls.incrementAndGet() }
        filter.doFilter(request, response, chain)
        return response
    }
}

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

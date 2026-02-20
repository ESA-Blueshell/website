package net.blueshell.api.infrastructure.security

import jakarta.servlet.FilterChain
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import java.util.concurrent.atomic.AtomicInteger

class PublicAuthRateLimitFilterTest {

    @Test
    fun `rate limits protected auth endpoint`() {
        val limiter = InMemoryRequestRateLimiter()
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
    fun `does not rate limit unrelated endpoints`() {
        val limiter = InMemoryRequestRateLimiter()
        val filter = PublicAuthRateLimitFilter(limiter)
        val chainCalls = AtomicInteger(0)

        repeat(20) {
            val response = invoke(filter, "GET", "/events", chainCalls)
            assertThat(response.status).isEqualTo(200)
        }

        assertThat(chainCalls.get()).isEqualTo(20)
    }

    private fun invoke(
        filter: PublicAuthRateLimitFilter,
        method: String,
        path: String,
        chainCalls: AtomicInteger,
    ): MockHttpServletResponse {
        val request = MockHttpServletRequest(method, path).apply {
            servletPath = path
            remoteAddr = "127.0.0.1"
        }
        val response = MockHttpServletResponse()
        val chain = FilterChain { _, _ -> chainCalls.incrementAndGet() }
        filter.doFilter(request, response, chain)
        return response
    }
}

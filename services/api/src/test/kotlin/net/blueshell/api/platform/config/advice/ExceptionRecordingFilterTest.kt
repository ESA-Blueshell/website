package net.blueshell.api.platform.config.advice

import jakarta.servlet.FilterChain
import jakarta.servlet.ServletException
import net.blueshell.api.exceptions.api.ExceptionConcern
import net.blueshell.api.exceptions.api.ExceptionRecorder
import net.blueshell.api.exceptions.api.ExceptionSource
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.same
import org.mockito.kotlin.verify
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.web.servlet.HandlerMapping

class ExceptionRecordingFilterTest {
    private val recorder: ExceptionRecorder = mock()
    private val filter = ExceptionRecordingFilter(recorder)
    private val resolver = ExceptionLoggingResolver()

    private fun request(pattern: String?) =
        MockHttpServletRequest("GET", "/events/5").apply {
            if (pattern != null) setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, pattern)
        }

    @Test
    fun `records an exception that became a server error, under the route's pattern`() {
        val thrown = IllegalStateException("broke")
        val request = request("/events/{id}")
        val response = MockHttpServletResponse()

        filter.doFilter(request, response) { req, res ->
            resolver.resolveException(request, response, null, thrown)
            (res as MockHttpServletResponse).status = 500
        }

        verify(recorder).record(same(thrown), eq(ExceptionConcern(ExceptionSource.REQUEST, "GET /events/{id}", null)))
    }

    @Test
    fun `leaves alone an exception answered as a refusal`() {
        val request = request("/events/{id}")
        val response = MockHttpServletResponse()

        filter.doFilter(request, response) { _, res ->
            resolver.resolveException(request, response, null, IllegalArgumentException("no"))
            (res as MockHttpServletResponse).status = 400
        }
        filter.doFilter(request(null), MockHttpServletResponse(), FilterChain { _, _ -> })

        verify(recorder, never()).record(any(), any())
    }

    @Test
    fun `records an exception that escaped the dispatcher, and lets it go on`() {
        val cause = IllegalStateException("deep")
        val escaped = ServletException("wrapped", cause)

        assertThatThrownBy {
            filter.doFilter(request(null), MockHttpServletResponse()) { _, _ -> throw escaped }
        }.isSameAs(escaped)

        verify(recorder).record(same(cause), eq(ExceptionConcern(ExceptionSource.REQUEST, "GET an unmatched route", null)))
    }

    @Test
    fun `records what the resolver saw when the dispatcher rethrows it`() {
        val thrown = IllegalStateException("resolved first")
        val request = request("/events/{id}")
        val plain = RuntimeException("plain")

        assertThatThrownBy {
            filter.doFilter(request, MockHttpServletResponse()) { _, response ->
                resolver.resolveException(request, response as MockHttpServletResponse, null, thrown)
                throw ServletException(plain)
            }
        }.isInstanceOf(ServletException::class.java)
        assertThatThrownBy { filter.doFilter(request(null), MockHttpServletResponse()) { _, _ -> throw plain } }.isSameAs(plain)

        verify(recorder).record(same(thrown), any())
        verify(recorder).record(same(plain), any())
    }
}

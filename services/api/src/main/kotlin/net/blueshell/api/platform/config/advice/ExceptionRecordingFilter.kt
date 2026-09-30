package net.blueshell.api.platform.config.advice

import jakarta.servlet.FilterChain
import jakarta.servlet.ServletException
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import net.blueshell.api.exceptions.api.ExceptionConcern
import net.blueshell.api.exceptions.api.ExceptionRecorder
import net.blueshell.api.exceptions.api.ExceptionSource
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import org.springframework.web.servlet.HandlerMapping

/**
 * Records every exception a request did not handle: one that became a 5xx, or one that escaped
 * the dispatcher altogether. A refusal the advice turned into a 4xx is an answer, not a fault.
 *
 * The concern is the route's pattern rather than its path, so ids group together and a token
 * in a path never reaches the record.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
class ExceptionRecordingFilter(
    private val recorder: ExceptionRecorder,
) : OncePerRequestFilter() {
    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        try {
            filterChain.doFilter(request, response)
        } catch (escaped: Exception) {
            recorder.record(resolved(request) ?: unwrapped(escaped), concernOf(request))
            throw escaped
        }
        val resolved = resolved(request)
        if (resolved != null && response.status >= SERVER_ERROR) {
            recorder.record(resolved, concernOf(request))
        }
    }

    private fun resolved(request: HttpServletRequest): Throwable? = request.getAttribute(RESOLVED_EXCEPTION) as? Throwable

    private fun unwrapped(escaped: Exception): Throwable = (escaped as? ServletException)?.rootCause ?: escaped

    private fun concernOf(request: HttpServletRequest): ExceptionConcern {
        val route = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE) as? String ?: "an unmatched route"
        return ExceptionConcern(ExceptionSource.REQUEST, "${request.method} $route", null)
    }

    companion object {
        const val RESOLVED_EXCEPTION = "net.blueshell.api.resolvedException"
        private const val SERVER_ERROR = 500
    }
}

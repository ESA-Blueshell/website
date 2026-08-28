package net.blueshell.api.platform.config.advice

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import net.blueshell.api.shared.util.sanitizeForLog
import org.slf4j.LoggerFactory
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.web.servlet.HandlerExceptionResolver
import org.springframework.web.servlet.ModelAndView

// Observer-only HandlerExceptionResolver that guarantees every
// exception escaping a controller lands in the logs.
//
// It runs first in the resolver chain (HIGHEST_PRECEDENCE), records
// one ERROR line with the request method, URI, exception class and
// message, attaches the full stack trace, and returns null. Because
// it does not produce a ModelAndView the composite resolver keeps
// walking — Spring Boot's ProblemDetailsExceptionHandler still maps
// ResponseStatusException to its declared status,
// ExceptionTranslationFilter still translates AccessDeniedException
// to 401/403, and the servlet error dispatch still surfaces anything
// left over as a 500 ProblemDetail.
//
// A regular @RestControllerAdvice cannot fill this role:
// auto-configured handlers such as ProblemDetailsExceptionHandler
// win on more specific exception types and translate the exception
// before any user-defined advice with lower precedence is consulted,
// so the catch-all would never see ResponseStatusException.
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
class ExceptionLoggingResolver : HandlerExceptionResolver {
    private val log = LoggerFactory.getLogger(ExceptionLoggingResolver::class.java)

    override fun resolveException(
        request: HttpServletRequest,
        response: HttpServletResponse,
        handler: Any?,
        ex: Exception,
    ): ModelAndView? {
        log.error(
            "{} {} -> {}: {}",
            sanitizeForLog(request.method),
            sanitizeForLog(request.requestURI),
            ex.javaClass.simpleName,
            sanitizeForLog(ex.message),
            ex,
        )
        return null
    }
}

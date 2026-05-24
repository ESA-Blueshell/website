package net.blueshell.api.platform.config.advice

import jakarta.servlet.http.HttpServletRequest
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.http.ProblemDetail
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.server.ResponseStatusException
import java.net.URI

// Fallback advice: Spring's built-in ResponseStatusExceptionResolver
// translates ResponseStatusException to an HTTP response silently —
// no stack trace anywhere — which made unrelated 5xx upload failures
// invisible in pod logs.
//
// The Exception handler logs at ERROR and rethrows so the framework
// keeps doing its normal job: Spring Security's
// ExceptionTranslationFilter translates AccessDeniedException to 403
// and AuthenticationException to 401, more specific @ExceptionHandler
// methods in sibling advices keep precedence (Spring picks the most
// specific match first), and the default resolvers still produce the
// appropriate ProblemDetail for framework exceptions. The only effect
// added here is a guaranteed ERROR log line with a stack trace.
@RestControllerAdvice
@Order(Ordered.LOWEST_PRECEDENCE)
class ExceptionLoggingAdvice {
    private val log = LoggerFactory.getLogger(javaClass)

    @ExceptionHandler(ResponseStatusException::class)
    fun handleResponseStatus(
        ex: ResponseStatusException,
        request: HttpServletRequest,
    ): ResponseEntity<ProblemDetail> {
        log.error(
            "{} {} -> {} ({})",
            request.method,
            request.requestURI,
            ex.statusCode,
            ex.reason,
            ex,
        )
        val pd = ex.body
        pd.instance = URI.create(request.requestURI)
        MDC.get("traceId")?.let { pd.setProperty("traceId", it) }
        return ResponseEntity.status(ex.statusCode).headers(ex.headers).body(pd)
    }

    @ExceptionHandler(Exception::class)
    fun handleException(ex: Exception, request: HttpServletRequest): Nothing {
        log.error("{} {} -> {}", request.method, request.requestURI, ex.javaClass.simpleName, ex)
        throw ex
    }
}
package net.blueshell.api.platform.config.advice

import jakarta.servlet.http.HttpServletRequest
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.server.ResponseStatusException
import java.net.URI

// Fallback advice: Spring's built-in ResponseStatusExceptionResolver
// translates ResponseStatusException to an HTTP response silently —
// no stack trace anywhere — which made unrelated 5xx upload failures
// invisible in pod logs. This advice catches anything more specific
// advices (validation, optimistic locking, …) do not, logs the full
// stack trace at ERROR, and returns the same ProblemDetail payload
// the framework would have produced.
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
    fun handleException(
        ex: Exception,
        request: HttpServletRequest,
    ): ResponseEntity<ProblemDetail> {
        log.error("{} {} -> unhandled exception", request.method, request.requestURI, ex)
        val pd = ProblemDetail.forStatusAndDetail(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "Internal Server Error",
        )
        pd.instance = URI.create(request.requestURI)
        MDC.get("traceId")?.let { pd.setProperty("traceId", it) }
        return ResponseEntity.internalServerError().body(pd)
    }
}
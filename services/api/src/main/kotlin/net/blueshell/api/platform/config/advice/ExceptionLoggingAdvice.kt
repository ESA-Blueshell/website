package net.blueshell.api.platform.config.advice

import jakarta.servlet.http.HttpServletRequest
import org.slf4j.LoggerFactory
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

// Fallback advice that guarantees every exception escaping a
// controller reaches the logs. The handler is the most generic
// possible (`Exception`), runs after more specific advices in
// sibling @RestControllerAdvice classes (LOWEST_PRECEDENCE), and
// rethrows the original exception so the framework keeps doing its
// normal job:
//
//   - ResponseStatusException is still translated to its declared
//     status by ResponseStatusExceptionResolver.
//   - Spring Security's ExceptionTranslationFilter still maps
//     AccessDeniedException to 403 or 401 (anonymous), and
//     AuthenticationException to 401 via the configured entry
//     point.
//   - Anything left over reaches the servlet error dispatch and is
//     surfaced as a 500 ProblemDetail.
//
// Without this advice ResponseStatusException was translated
// silently (no log line, no stack trace), which made banner upload
// 500s invisible in pod logs.
@RestControllerAdvice
@Order(Ordered.LOWEST_PRECEDENCE)
class ExceptionLoggingAdvice {
    private val log = LoggerFactory.getLogger(ExceptionLoggingAdvice::class.java)

    @ExceptionHandler(Exception::class)
    fun logAndRethrow(ex: Exception, request: HttpServletRequest): Nothing {
        log.error(
            "{} {} -> {}: {}",
            request.method,
            request.requestURI,
            ex.javaClass.simpleName,
            ex.message,
            ex,
        )
        throw ex
    }
}

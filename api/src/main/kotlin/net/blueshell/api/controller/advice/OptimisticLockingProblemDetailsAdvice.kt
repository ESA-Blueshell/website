package net.blueshell.api.controller.advice

import jakarta.servlet.http.HttpServletRequest
import org.slf4j.MDC
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.dao.OptimisticLockingFailureException
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import java.net.URI

@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE + 1) // run after validation advice but before generic handlers
class OptimisticLockingProblemDetailsAdvice {
    @ExceptionHandler(OptimisticLockingFailureException::class)
    fun handleOptimisticLock(ex: OptimisticLockingFailureException, request: HttpServletRequest): ProblemDetail {
        val pd = ProblemDetail.forStatusAndDetail(
            HttpStatus.CONFLICT,
            "This resource was modified by someone else. Please refresh the page and try your changes again."
        )
        pd.type = URI.create("about:blank")
        pd.title = "Conflict"
        pd.instance = URI.create(request.requestURI)

        val traceId = MDC.get("traceId")
        if (traceId != null) {
            pd.setProperty("traceId", traceId)
        }

        // Optional: include a minimal hint about the entity if available (safe to expose)
        ex.getMostSpecificCause()
        if (ex.getMostSpecificCause().message != null) {
            // Avoid leaking internals; keep it generic
            pd.setProperty("reason", "Optimistic locking conflict")
        }

        return pd
    }
}
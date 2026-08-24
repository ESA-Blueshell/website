package net.blueshell.api.platform.config.advice

import jakarta.servlet.http.HttpServletRequest
import net.blueshell.api.shared.dto.bulk.BulkSelectionRejected
import org.slf4j.MDC
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import java.net.URI

/**
 * Reports a refused bulk selection as 409, in the same `errors` shape as a bean
 * validation failure so one client-side handler covers both, with the offending ids
 * added per error.
 *
 * 409 rather than 400: the request is well formed, and the mismatch is between the
 * client's view of the table and the current database. That distinction is the signal
 * to reload the rows rather than to correct the form.
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
class BulkSelectionProblemDetailsAdvice {
    @ExceptionHandler(BulkSelectionRejected::class)
    fun handleBulkSelectionRejected(
        ex: BulkSelectionRejected,
        request: HttpServletRequest,
    ): ProblemDetail {
        val problem = ProblemDetail.forStatusAndDetail(
            HttpStatus.CONFLICT,
            "The selection no longer matches the current data.",
        )
        problem.instance = URI.create(request.requestURI)
        problem.setProperty(
            "errors",
            ex.violations.map { violation ->
                linkedMapOf(
                    "objectName" to ex.objectName,
                    "field" to violation.field,
                    "message" to violation.message,
                    "code" to violation.code,
                    "values" to violation.values,
                )
            },
        )
        MDC.get("traceId")?.let { problem.setProperty("traceId", it) }
        return problem
    }
}

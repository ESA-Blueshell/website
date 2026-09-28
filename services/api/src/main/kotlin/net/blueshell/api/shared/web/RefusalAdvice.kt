package net.blueshell.api.shared.web

import jakarta.servlet.http.HttpServletRequest
import net.blueshell.api.shared.refusal.Refusal
import net.blueshell.api.shared.refusal.asProblem
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.http.ProblemDetail
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

/** Answers every module's refusal as a code and its facts. See ADR-026. */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
class RefusalAdvice {
    @ExceptionHandler(Refusal::class)
    fun handleRefusal(
        ex: Refusal,
        request: HttpServletRequest,
    ): ProblemDetail = ex.asProblem(request)
}

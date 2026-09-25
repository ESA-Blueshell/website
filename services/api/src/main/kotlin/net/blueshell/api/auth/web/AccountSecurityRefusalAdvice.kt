package net.blueshell.api.auth.web

import jakarta.servlet.http.HttpServletRequest
import net.blueshell.api.auth.domain.AccountLocked
import net.blueshell.api.auth.domain.AccountSecurityRefusal
import net.blueshell.api.security.StepUpRequiredException
import org.slf4j.MDC
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.security.authentication.LockedException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import java.net.URI

@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
class AccountSecurityRefusalAdvice {
    @ExceptionHandler(AccountSecurityRefusal::class)
    fun handleRefusal(
        ex: AccountSecurityRefusal,
        request: HttpServletRequest,
    ): ProblemDetail = problemOf(ex, request)

    @ExceptionHandler(StepUpRequiredException::class)
    fun handleStepUp(
        @Suppress("UNUSED_PARAMETER") ex: StepUpRequiredException,
        request: HttpServletRequest,
    ): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, ex.message).also {
            it.type = URI.create("about:blank")
            it.instance = URI.create(request.requestURI)
            it.setProperty("code", "StepUpRequired")
        }

    /** Only the password's owner gets here: the lock is checked after the password. */
    @ExceptionHandler(LockedException::class)
    fun handleLocked(
        @Suppress("UNUSED_PARAMETER") ex: LockedException,
        request: HttpServletRequest,
    ): ProblemDetail = problemOf(AccountLocked(), request)

    private fun problemOf(
        ex: AccountSecurityRefusal,
        request: HttpServletRequest,
    ): ProblemDetail {
        val problem = ProblemDetail.forStatusAndDetail(ex.status, ex.summary)
        problem.type = URI.create("about:blank")
        problem.instance = URI.create(request.requestURI)
        problem.setProperty("code", ex.code)
        ex.facts.forEach { (name, value) -> problem.setProperty(name, value) }
        MDC.get("traceId")?.let { problem.setProperty("traceId", it) }
        return problem
    }
}

package net.blueshell.api.auth.web

import jakarta.servlet.http.HttpServletRequest
import net.blueshell.api.auth.domain.AccountLocked
import net.blueshell.api.security.StepUpRequiredException
import net.blueshell.api.shared.refusal.asProblem
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.security.authentication.LockedException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import java.net.URI

/** The two sign-in answers that are not account security refusals of their own; those go through `RefusalAdvice`. */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
class AccountSecurityRefusalAdvice {
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
    ): ProblemDetail = AccountLocked().asProblem(request)
}

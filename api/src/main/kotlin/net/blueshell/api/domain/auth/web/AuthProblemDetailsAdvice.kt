package net.blueshell.api.domain.auth.web

import jakarta.servlet.http.HttpServletRequest
import net.blueshell.api.domain.auth.application.exception.InvalidRecoveryTokenException
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import java.net.URI

@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
class AuthProblemDetailsAdvice {
    @ExceptionHandler(InvalidRecoveryTokenException::class)
    fun handleInvalidRecoveryToken(
        ex: InvalidRecoveryTokenException,
        request: HttpServletRequest
    ): ProblemDetail {
        val pd = ProblemDetail.forStatusAndDetail(
            HttpStatus.NOT_FOUND,
            ex.message ?: "Invalid or expired recovery token"
        )
        pd.type = URI.create("about:blank")
        pd.instance = URI.create(request.requestURI)
        return pd
    }
}

package net.blueshell.api.auth.web

import jakarta.servlet.http.HttpServletRequest
import net.blueshell.api.auth.domain.*
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.security.core.AuthenticationException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import java.net.URI

@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
class AuthProblemDetailsAdvice {

    companion object {
        private const val GENERIC_RECOVERY_TOKEN_DETAIL = "Invalid or expired recovery token."
        private const val GENERIC_AUTH_FAILURE_DETAIL = "Invalid username or password."
    }

    @ExceptionHandler(InvalidRecoveryTokenException::class)
    fun handleInvalidRecoveryToken(
        @Suppress("UNUSED_PARAMETER") ex: InvalidRecoveryTokenException,
        request: HttpServletRequest
    ): ProblemDetail {
        val pd = ProblemDetail.forStatusAndDetail(
            HttpStatus.NOT_FOUND,
            GENERIC_RECOVERY_TOKEN_DETAIL
        )
        pd.type = URI.create("about:blank")
        pd.instance = URI.create(request.requestURI)
        return pd
    }

    @ExceptionHandler(
        ExpiredRecoveryTokenException::class,
        ConsumedRecoveryTokenException::class,
        MalformedRecoveryTokenException::class,
        InvalidTokenTypeException::class,
        TokenVerificationFailedException::class
    )
    fun handleSpecificRecoveryTokenExceptions(
        @Suppress("UNUSED_PARAMETER") ex: RecoveryTokenException,
        request: HttpServletRequest
    ): ProblemDetail {
        val pd = ProblemDetail.forStatusAndDetail(
            HttpStatus.BAD_REQUEST,
            GENERIC_RECOVERY_TOKEN_DETAIL
        )
        pd.type = URI.create("about:blank")
        pd.instance = URI.create(request.requestURI)
        return pd
    }

    @ExceptionHandler(AuthenticationException::class)
    fun handleAuthenticationException(
        @Suppress("UNUSED_PARAMETER") ex: AuthenticationException,
        request: HttpServletRequest
    ): ProblemDetail {
        val pd = ProblemDetail.forStatusAndDetail(
            HttpStatus.UNAUTHORIZED,
            GENERIC_AUTH_FAILURE_DETAIL
        )
        pd.type = URI.create("about:blank")
        pd.instance = URI.create(request.requestURI)
        return pd
    }
}

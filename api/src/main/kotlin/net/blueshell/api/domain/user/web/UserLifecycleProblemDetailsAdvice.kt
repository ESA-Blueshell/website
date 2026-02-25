package net.blueshell.api.domain.user.web

import jakarta.servlet.http.HttpServletRequest
import net.blueshell.api.domain.user.application.exception.DeletedUserNotFoundException
import net.blueshell.api.domain.user.application.exception.RestoreConflictException
import net.blueshell.api.domain.user.application.exception.RestoreWindowExpiredException
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import java.net.URI

@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
class UserLifecycleProblemDetailsAdvice {

    @ExceptionHandler(DeletedUserNotFoundException::class)
    fun handleDeletedUserNotFound(ex: DeletedUserNotFoundException, request: HttpServletRequest): ProblemDetail {
        val pd = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.message ?: "Deleted user not found.")
        pd.type = URI.create("about:blank")
        pd.instance = URI.create(request.requestURI)
        return pd
    }

    @ExceptionHandler(RestoreWindowExpiredException::class)
    fun handleRestoreWindowExpired(ex: RestoreWindowExpiredException, request: HttpServletRequest): ProblemDetail {
        val pd = ProblemDetail.forStatusAndDetail(HttpStatus.GONE, ex.message ?: "Restore window has expired.")
        pd.type = URI.create("about:blank")
        pd.instance = URI.create(request.requestURI)
        return pd
    }

    @ExceptionHandler(RestoreConflictException::class)
    fun handleRestoreConflict(ex: RestoreConflictException, request: HttpServletRequest): ProblemDetail {
        val pd = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.message ?: "Cannot restore user.")
        pd.type = URI.create("about:blank")
        pd.instance = URI.create(request.requestURI)
        return pd
    }
}

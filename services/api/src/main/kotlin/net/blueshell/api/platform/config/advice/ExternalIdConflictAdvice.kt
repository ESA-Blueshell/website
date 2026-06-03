package net.blueshell.api.platform.config.advice

import jakarta.servlet.http.HttpServletRequest
import net.blueshell.api.domain.user.application.UserService
import net.blueshell.api.platform.integration.sync.application.ExternalIdConflictException
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import java.net.URI

@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
class ExternalIdConflictAdvice(private val users: UserService) {

    @ExceptionHandler(ExternalIdConflictException::class)
    fun handle(ex: ExternalIdConflictException, request: HttpServletRequest): ProblemDetail {
        val pd = ProblemDetail.forStatusAndDetail(
            HttpStatus.CONFLICT,
            "External id '${ex.externalId}' on ${ex.system} is already linked to another user.",
        )
        pd.title = "Conflict"
        pd.instance = URI.create(request.requestURI)
        pd.setProperty("existingUserId", ex.existingUserId)
        pd.setProperty("system", ex.system.name)
        val existingUser = runCatching { users.findById(ex.existingUserId) }.getOrNull()
        pd.setProperty("existingUserFullName", existingUser?.fullName)
        return pd
    }
}

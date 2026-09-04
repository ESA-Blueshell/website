package net.blueshell.api.user.web

import jakarta.servlet.http.HttpServletRequest
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import java.net.URI

/**
 * A uniqueness rule on an account that only the database got to enforce.
 *
 * `UniqueUserCommandValidator` checks these before the insert, so arriving here means two
 * requests raced. Reported under the same field names and messages as that check, so a client
 * attaches it without knowing which of the two spoke. Lives with the aggregate whose constraints
 * it names (ADR-003) and rethrows anything it does not recognise, an integrity failure nobody
 * can retype their way out of being a bug.
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
class UserUniquenessAdvice {

    @ExceptionHandler(DataIntegrityViolationException::class)
    fun handleDataIntegrityViolation(
        ex: DataIntegrityViolationException,
        request: HttpServletRequest
    ): ProblemDetail {
        val (field, message) = UNIQUE_CONSTRAINT_FIELDS.entries
            .firstOrNull { (constraint, _) -> namesConstraint(ex, constraint) }
            ?.value
            ?: throw ex

        val pd = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Validation failed for request.")
        pd.type = URI.create("about:blank")
        pd.instance = URI.create(request.requestURI)
        pd.setProperty(
            "errors",
            listOf(
                linkedMapOf(
                    "objectName" to "User",
                    "field" to field,
                    "message" to message,
                    "code" to "Unique",
                )
            )
        )
        return pd
    }

    private companion object {
        /**
         * The unique constraints a person can do something about, and the field and
         * wording [net.blueshell.api.user.domain.UniqueUserCommandValidator] already
         * uses for each.
         */
        val UNIQUE_CONSTRAINT_FIELDS = mapOf(
            "uk_users_username_deleted_at" to ("username" to "Username is taken."),
            "uk_users_email_deleted_at" to ("email" to "Email is taken."),
            "uk_users_discord_deleted_at" to ("discord" to "Discord is taken."),
            "uk_users_phone_number_deleted_at" to ("phoneNumber" to "Phone number is taken."),
        )

        /**
         * Whether the failure names this constraint. The driver puts the name in its
         * own message, somewhere down the cause chain rather than on the Spring
         * exception, so the whole chain is what gets read.
         */
        fun namesConstraint(ex: Throwable, constraint: String): Boolean {
            var cause: Throwable? = ex
            while (cause != null) {
                if (cause.message?.contains(constraint, ignoreCase = true) == true) return true
                cause = cause.cause
            }
            return false
        }
    }
}

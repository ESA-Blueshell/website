package net.blueshell.api.platform.config.advice

import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.ConstraintViolation
import jakarta.validation.ConstraintViolationException
import net.blueshell.api.shared.dto.bulk.BulkFieldRejected
import org.slf4j.MDC
import org.springframework.core.Ordered
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.core.annotation.Order
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.validation.FieldError
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import tools.jackson.databind.exc.InvalidNullException
import tools.jackson.databind.exc.MismatchedInputException
import java.net.URI

@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE) // make sure this runs before any generic handlers
class ValidationProblemDetailsAdvice {
    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleMethodArgumentNotValid(
        ex: MethodArgumentNotValidException,
        request: HttpServletRequest
    ): ProblemDetail {
        val pd = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Validation failed for request.")
        pd.type = URI.create("about:blank")
        pd.instance = URI.create(request.requestURI)

        val errors = ex.bindingResult.fieldErrors.stream()
            .map { fe: FieldError? ->
                errorMap(
                    fe!!.objectName,
                    fe.field,
                    fe.defaultMessage,
                    fe.code
                )
            }
            .toList()

        pd.setProperty("errors", errors)
        val traceId = MDC.get("traceId")
        if (traceId != null) pd.setProperty("traceId", traceId)

        return pd
    }

    /**
     * Request DTOs declare mandatory fields as non-nullable Kotlin types, so a
     * body that omits one fails during deserialization instead of reaching bean
     * validation. Jackson names the offending property, which is enough to
     * report it in the same `errors` shape the validation handlers above use —
     * without it the client would get a bare 400 and lose the field mapping.
     */
    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun handleUnreadableBody(
        ex: HttpMessageNotReadableException,
        request: HttpServletRequest
    ): ProblemDetail {
        val pd = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Validation failed for request.")
        pd.instance = URI.create(request.requestURI)

        val errors = when (val cause = ex.cause) {
            is InvalidNullException -> listOf(
                errorMap(
                    cause.targetType?.simpleName,
                    cause.propertyName.simpleName,
                    "must not be null",
                    "NotNull"
                )
            )

            is MismatchedInputException -> listOf(
                errorMap(
                    cause.targetType?.simpleName,
                    cause.path.lastOrNull()?.propertyName,
                    "has an unexpected value",
                    "TypeMismatch"
                )
            )

            else -> emptyList()
        }

        pd.setProperty("errors", errors)
        val traceId = MDC.get("traceId")
        if (traceId != null) pd.setProperty("traceId", traceId)

        return pd
    }

    /**
     * A rule that needed the database, reported in the same `errors` shape as the bean
     * constraints above so the client attaches it to the field without a special case.
     */
    @ExceptionHandler(BulkFieldRejected::class)
    fun handleBulkFieldRejected(
        ex: BulkFieldRejected,
        request: HttpServletRequest
    ): ProblemDetail {
        val pd = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Validation failed for request.")
        pd.instance = URI.create(request.requestURI)
        pd.setProperty(
            "errors",
            ex.violations.map { errorMap(ex.objectName, it.field, it.message, it.code) }
        )
        val traceId = MDC.get("traceId")
        if (traceId != null) pd.setProperty("traceId", traceId)

        return pd
    }

    /**
     * A uniqueness rule that only the database got to enforce.
     *
     * [net.blueshell.api.user.domain.UniqueUserCommandValidator] checks these
     * before the insert and reports them per field, so reaching here means two
     * requests raced or a path exists that does not run that check. Either way the
     * applicant typed something a person can fix, and answering 500 told them to
     * report a bug and retry into the same wall. Reported under the same field
     * names and the same messages as the pre-insert check, so the client attaches
     * it without knowing which of the two spoke.
     */
    @ExceptionHandler(DataIntegrityViolationException::class)
    fun handleDataIntegrityViolation(
        ex: DataIntegrityViolationException,
        request: HttpServletRequest
    ): ProblemDetail {
        val violated = UNIQUE_CONSTRAINT_FIELDS.entries
            .firstOrNull { (constraint, _) -> namesConstraint(ex, constraint) }

        val status = if (violated == null) HttpStatus.CONFLICT else HttpStatus.BAD_REQUEST
        val detail = if (violated == null) "This conflicts with something already stored."
        else "Validation failed for request."

        val pd = ProblemDetail.forStatusAndDetail(status, detail)
        pd.type = URI.create("about:blank")
        pd.instance = URI.create(request.requestURI)

        if (violated != null) {
            val (field, message) = violated.value
            pd.setProperty("errors", listOf(errorMap("User", field, message, "Unique")))
        }

        val traceId = MDC.get("traceId")
        if (traceId != null) pd.setProperty("traceId", traceId)

        return pd
    }

    @ExceptionHandler(ConstraintViolationException::class)
    fun handleConstraintViolation(
        ex: ConstraintViolationException,
        request: HttpServletRequest
    ): ProblemDetail {
        val pd = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Validation failed for request.")
        pd.instance = URI.create(request.requestURI)

        val errors = ex.constraintViolations.stream()
            .map { cv: ConstraintViolation<*>? ->
                errorMap(
                    cv!!.rootBeanClass.simpleName,
                    cv.propertyPath.toString(),
                    cv.message,
                    cv.constraintDescriptor.annotation.annotationClass.simpleName
                )
            }
            .toList()

        pd.setProperty("errors", errors)
        val traceId = MDC.get("traceId")
        if (traceId != null) pd.setProperty("traceId", traceId)

        return pd
    }


    companion object {
        /**
         * The unique constraints a person can do something about, and the field and
         * wording [net.blueshell.api.user.domain.UniqueUserCommandValidator] already
         * uses for each. Anything absent here is a conflict the client cannot fix by
         * retyping, and stays a bare 409.
         */
        private val UNIQUE_CONSTRAINT_FIELDS = linkedMapOf(
            "uk_users_username_deleted_at" to ("username" to "Username is taken."),
            "uk_users_email_deleted_at" to ("email" to "Email is taken."),
            "uk_users_discord_deleted_at" to ("discord" to "Discord is taken."),
            "uk_users_phone_number_deleted_at" to ("phoneNumber" to "Phone number is taken."),
        )

        /**
         * Whether the failure names this constraint. The name appears in the driver's
         * own message, which sits somewhere down the cause chain rather than on the
         * Spring exception, so the whole chain is what gets read.
         */
        private fun namesConstraint(ex: Throwable, constraint: String): Boolean {
            var cause: Throwable? = ex
            while (cause != null) {
                if (cause.message?.contains(constraint, ignoreCase = true) == true) return true
                cause = cause.cause
            }
            return false
        }

        /**
         * Builds a Map that mirrors the previous Map.of(...) structure but allows null values.
         * Uses LinkedHashMap to preserve key insertion order.
         */
        private fun errorMap(
            objectName: String?,
            field: String?,
            message: String?,
            code: String?
        ): MutableMap<String, Any?> {
            val m: MutableMap<String, Any?> = LinkedHashMap()
            m["objectName"] = objectName
            m["field"] = field
            m["message"] = message
            m["code"] = code
            return m
        }
    }
}

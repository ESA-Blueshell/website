package net.blueshell.api.controller.advice

import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.ConstraintViolation
import jakarta.validation.ConstraintViolationException
import org.slf4j.MDC
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.validation.FieldError
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
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
            .map<MutableMap<String?, Any?>?> { fe: FieldError? ->
                errorMap(
                    fe!!.objectName,
                    fe.field,
                    fe.rejectedValue,
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

    @ExceptionHandler(ConstraintViolationException::class)
    fun handleConstraintViolation(
        ex: ConstraintViolationException,
        request: HttpServletRequest
    ): ProblemDetail {
        val pd = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Validation failed for request.")
        pd.instance = URI.create(request.requestURI)

        val errors = ex.constraintViolations.stream()
            .map<MutableMap<String?, Any?>?> { cv: ConstraintViolation<*>? ->
                errorMap(
                    cv!!.getRootBeanClass().getSimpleName(),
                    cv.propertyPath.toString(),
                    cv.invalidValue,
                    cv.message,
                    cv.constraintDescriptor.getAnnotation().annotationType().getSimpleName()
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
         * Builds a Map that mirrors the previous Map.of(...) structure but allows null values.
         * Uses LinkedHashMap to preserve key insertion order.
         */
        private fun errorMap(
            objectName: String?,
            field: String?,
            rejectedValue: Any?,
            message: String?,
            code: String?
        ): MutableMap<String?, Any?> {
            val m: MutableMap<String?, Any?> = LinkedHashMap<String?, Any?>()
            m.put("objectName", objectName)
            m.put("field", field)
            m.put("rejectedValue", rejectedValue)
            m.put("message", message)
            m.put("code", code)
            return m
        }
    }
}

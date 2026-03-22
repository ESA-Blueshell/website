package net.blueshell.api.platform.config.advice

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

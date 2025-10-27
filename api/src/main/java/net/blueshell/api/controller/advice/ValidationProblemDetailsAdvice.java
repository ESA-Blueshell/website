package net.blueshell.api.controller.advice;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;
import java.util.List;
import java.util.Map;

@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE) // make sure this runs before any generic handlers
public class ValidationProblemDetailsAdvice {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
                                                      HttpServletRequest request) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Validation failed for request.");
        pd.setType(URI.create("about:blank"));
        pd.setInstance(URI.create(request.getRequestURI()));

        List<Map<String, Object>> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> Map.of(
                        "objectName", fe.getObjectName(),
                        "field", fe.getField(),
                        "rejectedValue", fe.getRejectedValue(),
                        "message", fe.getDefaultMessage(),
                        "code", fe.getCode()
                ))
                .toList();

        pd.setProperty("errors", errors);
        String traceId = MDC.get("traceId");
        if (traceId != null) pd.setProperty("traceId", traceId);

        return pd;
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ProblemDetail handleConstraintViolation(ConstraintViolationException ex,
                                                   HttpServletRequest request) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Validation failed for request.");
        pd.setInstance(URI.create(request.getRequestURI()));

        List<Map<String, Object>> errors = ex.getConstraintViolations().stream()
                .map(cv -> Map.of(
                        "objectName", cv.getRootBeanClass().getSimpleName(),
                        "field", cv.getPropertyPath().toString(),
                        "rejectedValue", cv.getInvalidValue(),
                        "message", cv.getMessage(),
                        "code", cv.getConstraintDescriptor().getAnnotation().annotationType().getSimpleName()
                ))
                .toList();

        pd.setProperty("errors", errors);
        String traceId = MDC.get("traceId");
        if (traceId != null) pd.setProperty("traceId", traceId);

        return pd;
    }
}


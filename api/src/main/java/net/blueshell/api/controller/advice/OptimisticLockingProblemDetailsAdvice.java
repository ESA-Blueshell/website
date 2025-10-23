package net.blueshell.api.controller.advice;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;

@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE + 1) // run after validation advice but before generic handlers
public class OptimisticLockingProblemDetailsAdvice {

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ProblemDetail handleOptimisticLock(OptimisticLockingFailureException ex, HttpServletRequest request) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT,
                "This resource was modified by someone else. Please refresh the page and try your changes again."
        );
        pd.setType(URI.create("about:blank"));
        pd.setTitle("Conflict");
        pd.setInstance(URI.create(request.getRequestURI()));

        String traceId = MDC.get("traceId");
        if (traceId != null) {
            pd.setProperty("traceId", traceId);
        }

        // Optional: include a minimal hint about the entity if available (safe to expose)
        ex.getMostSpecificCause();
        if (ex.getMostSpecificCause().getMessage() != null) {
            // Avoid leaking internals; keep it generic
            pd.setProperty("reason", "Optimistic locking conflict");
        }

        return pd;
    }
}
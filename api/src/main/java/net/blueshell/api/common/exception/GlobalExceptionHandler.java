package net.blueshell.api.common.exception;

import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Centralised REST-level error handling.
 * <p>
 * – Returns JSON for every exception.<br/>
 * – Bean-Validation errors are aggregated into a structured list.<br/>
 * – Falls back to a generic 500 handler for anything not explicitly mapped.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    /*───────────────────────────  BEAN VALIDATION  ───────────────────────────*/

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ValidationErrorResponse> handleBodyValidation(MethodArgumentNotValidException ex) {
        ex.printStackTrace();
        List<ValidationErrorResponse.Violation> violations = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(this::toViolation)
                .collect(Collectors.toList());
        return buildValidationResponse(violations);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ValidationErrorResponse> handlePathQueryValidation(ConstraintViolationException ex) {
        ex.printStackTrace();
        List<ValidationErrorResponse.Violation> violations = ex.getConstraintViolations()
                .stream()
                .map(cv -> new ValidationErrorResponse.Violation(cv.getPropertyPath().toString(), cv.getMessage()))
                .collect(Collectors.toList());
        return buildValidationResponse(violations);
    }

    /*────────────────────────────  DOMAIN EXCEPTIONS  ────────────────────────*/

    @ExceptionHandler(FileNotFoundException.class)
    public ResponseEntity<Object> handleFileNotFoundException(FileNotFoundException ex, WebRequest request) {
        ex.printStackTrace();
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(createErrorResponse(ex.getMessage()));
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<Object> handleResourceNotFoundException(ResourceNotFoundException ex, WebRequest request) {
        ex.printStackTrace();
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(createErrorResponse(ex.getMessage()));
    }

    @ExceptionHandler(MissingParameterException.class)
    public ResponseEntity<Object> handleMissingParameterException(MissingParameterException ex, WebRequest request) {
        ex.printStackTrace();
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(createErrorResponse(ex.getMessage()));
    }

    @ExceptionHandler(InvalidFormException.class)
    public ResponseEntity<Object> handleInvalidFormException(InvalidFormException ex, WebRequest request) {
        ex.printStackTrace();
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(createErrorResponse(ex.getMessage()));
    }

    @ExceptionHandler(PermissionDeniedException.class)
    public ResponseEntity<Object> handlePermissionDeniedException(PermissionDeniedException ex, WebRequest request) {
        ex.printStackTrace();
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(createErrorResponse(ex.getMessage()));
    }

    @ExceptionHandler(AuthorizationDeniedException.class)
    public ResponseEntity<Object> handleAuthorizationDeniedException(AuthorizationDeniedException ex, WebRequest request) {
        ex.printStackTrace();
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(createErrorResponse(ex.getMessage()));
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<Object> handleBadCredentialsException(BadCredentialsException ex, WebRequest request) {
        ex.printStackTrace();
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(createErrorResponse(ex.getMessage()));
    }

    /*────────────────────────────  FALLBACK  ────────────────────────────────*/

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> handleAllExceptions(Exception ex, WebRequest request) {
        ex.printStackTrace();
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(createErrorResponse(ex.getMessage()));
    }

    /*────────────────────────────  HELPERS  ─────────────────────────────────*/

    private ResponseEntity<ValidationErrorResponse> buildValidationResponse(List<ValidationErrorResponse.Violation> violations) {
        ValidationErrorResponse body = new ValidationErrorResponse(
                HttpStatus.BAD_REQUEST.value(),
                "Validation failed",
                Instant.now(),
                violations);
        return ResponseEntity.badRequest().body(body);
    }

    private ValidationErrorResponse.Violation toViolation(FieldError fe) {
        return new ValidationErrorResponse.Violation(fe.getField(), fe.getDefaultMessage());
    }

    /**
     * Helper method to create a standardized error response for non-validation exceptions.
     */
    private Map<String, String> createErrorResponse(String message) {
        Map<String, String> errorResponse = new HashMap<>();
        errorResponse.put("error", message);
        errorResponse.put("timestamp", LocalDateTime.now().toString());
        return errorResponse;
    }

    /*──────────────────────  DTOs  ──────────────────────*/

    /**
     * Response body for Bean-Validation failures.
     */
    public record ValidationErrorResponse(
            int status,
            String error,
            Instant timestamp,
            List<Violation> violations) {
        public record Violation(String field, String message) { }
    }
}

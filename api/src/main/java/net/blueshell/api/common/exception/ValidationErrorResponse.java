package net.blueshell.api.common.exception;

import java.time.Instant;
import java.util.List;

public record ValidationErrorResponse(
        int status,
        String error,
        Instant timestamp,
        List<Violation> violations
) {
    public record Violation(String field, String message) { }
}


package net.blueshell.api.shared.validation

class DatabaseValidationException(
    val errors: List<ValidationError>
) : RuntimeException("Validation failed for request.")

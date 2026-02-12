package net.blueshell.api.shared.validation

data class ValidationError(
    val objectName: String,
    val field: String,
    val rejectedValue: Any?,
    val message: String,
    val code: String
)

class DatabaseValidationErrors(private val objectName: String) {
    private val errors = mutableListOf<ValidationError>()

    fun reject(field: String, rejectedValue: Any?, message: String, code: String) {
        errors.add(ValidationError(objectName, field, rejectedValue, message, code))
    }

    fun throwIfAny() {
        if (errors.isNotEmpty()) {
            throw DatabaseValidationException(errors)
        }
    }
}

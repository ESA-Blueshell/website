package net.blueshell.api.feature.file.validation

import jakarta.validation.Constraint
import jakarta.validation.Payload
import kotlin.reflect.KClass

@MustBeDocumented
@Target(AnnotationTarget.FIELD, AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
@Constraint(validatedBy = [FileSizeValidator::class])
annotation class FileSize(
    val message: String = "File size must be between {min} and {max} bytes",
    val groups: Array<KClass<*>> = [],
    val payload: Array<KClass<out Payload>> = [],
    /**
     * Inclusive min (bytes)
     */
    val min: Long = 0L,
    /**
     * Inclusive max (bytes)
     */
    val max: Long,
    /**
     * Whether an empty upload (isEmpty) is allowed
     */
    val allowEmpty: Boolean = false
)


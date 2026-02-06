package net.blueshell.api.validation.user

import jakarta.validation.Constraint
import jakarta.validation.Payload
import kotlin.reflect.KClass

/**
 * Custom annotation to ensure the uniqueness of the phone number.
 */
@MustBeDocumented
@Constraint(validatedBy = [UniquePhoneNumberValidator::class])
@Target(AnnotationTarget.FIELD)
@Retention(AnnotationRetention.RUNTIME)
annotation class UniquePhoneNumber(
    val message: String = "Phone number is already in use.",
    val groups: Array<KClass<*>> = [],
    val payload: Array<KClass<out Payload>> = []
)

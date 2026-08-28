package net.blueshell.api.user.web

import jakarta.validation.Constraint
import jakarta.validation.Payload
import kotlin.reflect.KClass

/**
 * Custom annotation to validate that a phone number is a valid mobile number.
 */
@MustBeDocumented
@Constraint(validatedBy = [ValidMobilePhoneNumberValidator::class])
@Target(AnnotationTarget.FIELD)
@Retention(AnnotationRetention.RUNTIME)
annotation class ValidMobilePhoneNumber(
    val message: String = "Invalid mobile phone number.",
    val groups: Array<KClass<*>> = [],
    val payload: Array<KClass<out Payload>> = []
)

package net.blueshell.api.feature.event.validation

import jakarta.validation.Constraint
import jakarta.validation.Payload
import kotlin.reflect.KClass

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@MustBeDocumented
@Constraint(validatedBy = [ValidEventSignUpValidator::class])
annotation class ValidEventSignUp(
    val message: String = "Invalid event sign-up payload",
    val groups: Array<KClass<*>> = [],
    val payload: Array<KClass<out Payload>> = []
)

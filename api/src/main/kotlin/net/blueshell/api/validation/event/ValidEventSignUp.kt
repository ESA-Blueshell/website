package net.blueshell.api.validation.event

import jakarta.validation.Constraint
import jakarta.validation.Payload
import kotlin.reflect.KClass

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@MustBeDocumented
@Constraint(validatedBy = [_root_ide_package_.net.blueshell.api.validation.event.ValidEventSignUpValidator::class])
annotation class ValidEventSignUp(
    val message: String = "Invalid event sign-up payload",
    val groups: Array<KClass<*>> = [],
    val payload: Array<KClass<out Payload>> = []
)

package net.blueshell.api.domain.event.web.validation

import jakarta.validation.Constraint
import jakarta.validation.Payload
import kotlin.reflect.KClass

@MustBeDocumented
@Constraint(validatedBy = [ValidSignUpDeadlineValidator::class])
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class ValidSignUpDeadline(
    val message: String = "Invalid sign-up deadline",
    val groups: Array<KClass<*>> = [],
    val payload: Array<KClass<out Payload>> = []
)

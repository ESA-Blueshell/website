package net.blueshell.api.user.web.validation

import jakarta.validation.Constraint
import jakarta.validation.Payload
import kotlin.reflect.KClass

/**
 * Custom annotation to ensure the uniqueness of the username.
 */
@MustBeDocumented
@Constraint(validatedBy = [ExistingUsernameValidator::class])
@Target(AnnotationTarget.FIELD)
@Retention(AnnotationRetention.RUNTIME)
annotation class ExistingUsername(
    val message: String = "Username is not known.",
    val groups: Array<KClass<*>> = [],
    val payload: Array<KClass<out Payload>> = []
)

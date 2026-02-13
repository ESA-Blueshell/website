package net.blueshell.api.shared.validation

import jakarta.validation.Constraint
import jakarta.validation.Payload
import net.blueshell.api.domain.user.application.validation.UniqueUserCommandValidator
import kotlin.reflect.KClass

@MustBeDocumented
@Constraint(validatedBy = [UniqueUserCommandValidator::class])
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class UniqueUserCommand(
    val message: String = "User has duplicate fields.",
    val groups: Array<KClass<*>> = [],
    val payload: Array<KClass<out Payload>> = []
)

package net.blueshell.api.shared.validation

import jakarta.validation.Constraint
import jakarta.validation.Payload
import net.blueshell.api.domain.user.application.validation.UniqueUsernameValidator
import kotlin.reflect.KClass

@MustBeDocumented
@Constraint(validatedBy = [UniqueUsernameValidator::class])
@Target(AnnotationTarget.FIELD)
@Retention(AnnotationRetention.RUNTIME)
annotation class UniqueUsername(
    val message: String = "Username is taken.",
    val groups: Array<KClass<*>> = [],
    val payload: Array<KClass<out Payload>> = []
)

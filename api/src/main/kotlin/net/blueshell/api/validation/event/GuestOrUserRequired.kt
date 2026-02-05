package net.blueshell.api.validation.event

import jakarta.validation.Constraint
import jakarta.validation.Payload
import kotlin.reflect.KClass

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@MustBeDocumented
@Constraint(validatedBy = [_root_ide_package_.net.blueshell.api.validation.event.GuestOrUserRequiredValidator::class])
annotation class GuestOrUserRequired(
    val message: String = "Either guest or user must be provided.",
    val groups: Array<KClass<*>> = [],
    val payload: Array<KClass<out Payload>> = []
)

package net.blueshell.api.domain.membership.web.validation

import jakarta.validation.Constraint
import jakarta.validation.Payload
import kotlin.reflect.KClass

/**
 * Custom annotation to ensure the uniqueness of the username.
 */
@MustBeDocumented
@Constraint(validatedBy = [NoExistingMembershipForUserIdValidator::class])
@Target(AnnotationTarget.FIELD)
@Retention(AnnotationRetention.RUNTIME)
annotation class NoExistingMembershipForUserId(
    val message: String = "User is already a member.",
    val groups: Array<KClass<*>> = [],
    val payload: Array<KClass<out Payload>> = []
)

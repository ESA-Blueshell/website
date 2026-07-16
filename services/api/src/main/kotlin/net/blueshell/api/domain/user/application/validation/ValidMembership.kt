package net.blueshell.api.domain.user.application.validation

import jakarta.validation.Constraint
import jakarta.validation.Payload
import kotlin.reflect.KClass

/**
 * Class-level constraint enforcing the membership interval invariants that
 * span fields and other rows: `startDate < endDate`, at most one active
 * membership per user, and no overlapping intervals. Applied to commands that
 * implement [MembershipIntervalCandidate] so violations surface through the
 * shared `ConstraintViolationException` handling.
 */
@MustBeDocumented
@Constraint(validatedBy = [MembershipValidator::class])
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class ValidMembership(
    val message: String = "Invalid membership interval.",
    val groups: Array<KClass<*>> = [],
    val payload: Array<KClass<out Payload>> = []
)

package net.blueshell.api.user.api

import jakarta.validation.Constraint
import jakarta.validation.ConstraintValidator
import jakarta.validation.ConstraintValidatorContext
import jakarta.validation.Payload
import java.sql.Date
import kotlin.reflect.KClass

/**
 * A member profile as a write leaves it, and whether the person it belongs to is a member.
 *
 * Anybody may keep a profile, but a member's must name a date of birth and a nationality: the
 * association reports both for its members, and nobody else's are needed.
 * TWIN: UserForm's `memberProfileRequired`, which asks for the same two fields on the page.
 */
@CompleteForMembers
data class MemberProfileCompleteness(
    val member: Boolean,
    val dateOfBirth: Date?,
    val nationality: String?,
)

@MustBeDocumented
@Constraint(validatedBy = [CompleteForMembersValidator::class])
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class CompleteForMembers(
    val message: String = "A member's profile names a date of birth and a nationality.",
    val groups: Array<KClass<*>> = [],
    val payload: Array<KClass<out Payload>> = [],
)

class CompleteForMembersValidator : ConstraintValidator<CompleteForMembers, MemberProfileCompleteness> {
    override fun isValid(
        candidate: MemberProfileCompleteness?,
        context: ConstraintValidatorContext,
    ): Boolean {
        if (candidate == null || !candidate.member) return true
        val missing =
            listOfNotNull(
                "memberProfile.dateOfBirth".takeIf { candidate.dateOfBirth == null },
                "memberProfile.nationality".takeIf { candidate.nationality.isNullOrBlank() },
            )
        if (missing.isEmpty()) return true
        context.disableDefaultConstraintViolation()
        missing.forEach { field ->
            context
                .buildConstraintViolationWithTemplate("A member fills this in.")
                .addPropertyNode(field)
                .addConstraintViolation()
        }
        return false
    }
}

/** The completeness a write of [this] profile is held to, for somebody who is a [member] or not. */
fun UpsertMemberProfileData.completenessFor(member: Boolean) = MemberProfileCompleteness(member, dateOfBirth, nationality)

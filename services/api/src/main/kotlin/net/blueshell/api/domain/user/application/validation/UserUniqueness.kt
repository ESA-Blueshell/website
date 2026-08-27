package net.blueshell.api.domain.user.application.validation

/**
 * The fields a user write must not collide on, validated against
 * [UniqueUserCommand] before the write is applied. [subjectId] is the row being
 * edited, or null when creating, so an edit does not read as a conflict with
 * itself.
 *
 * The signup routes do not use this: the account is only known once the token
 * resolves, so they check uniqueness imperatively (ADR-024).
 */
@UniqueUserCommand
data class UserUniqueness(
    override val subjectId: Long?,
    override val username: String? = null,
    override val email: String? = null,
    override val discord: String? = null,
    override val phoneNumber: String? = null,
) : UserUniquenessCandidate

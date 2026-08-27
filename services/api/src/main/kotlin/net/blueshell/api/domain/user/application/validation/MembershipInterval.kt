package net.blueshell.api.domain.user.application.validation

import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.PastOrPresent
import java.time.LocalDate

/**
 * The interval a membership write would leave behind, validated against
 * [ValidMembership] before it is applied. Create, correct and the lifecycle
 * operations all reduce to this shape, so the invariants are stated once.
 */
@ValidMembership
data class MembershipInterval(
    @field:NotNull(message = "User ID is required")
    val userId: Long?,
    /** Id of the membership being edited, or null when creating a new one. */
    val id: Long? = null,
    @field:NotNull(message = "Start date is required")
    @field:PastOrPresent(message = "Start date cannot be in the future")
    val startDate: LocalDate?,
    @field:PastOrPresent(message = "End date cannot be in the future")
    val endDate: LocalDate? = null,
) : MembershipIntervalCandidate {
    override val candidateUserId: Long? get() = userId
    override val candidateMembershipId: Long? get() = id
    override val candidateStartDate: LocalDate? get() = startDate
    override val candidateEndDate: LocalDate? get() = endDate
}

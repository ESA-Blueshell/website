package net.blueshell.api.domain.user.command

import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.PastOrPresent
import jakarta.validation.constraints.Positive
import net.blueshell.api.domain.user.application.query.MembershipQuery
import net.blueshell.api.domain.user.application.validation.MembershipIntervalCandidate
import net.blueshell.api.domain.user.application.validation.ValidMembership
import net.blueshell.api.domain.user.persistence.Membership
import net.blueshell.api.shared.command.Command
import net.blueshell.api.shared.enums.MemberType
import java.time.LocalDate

data class FindMembershipsCommand(
    val filter: MembershipQuery
) : Command<MutableList<Membership>>

@ValidMembership
data class CreateMembershipCommand(
    val userId: Long,
    @field:NotNull(message = "isMember flag is required")
    val isMember: Boolean?,
    @field:NotNull(message = "hasAddress flag is required")
    val hasAddress: Boolean?,
    @field:NotNull(message = "hasMemberProfile flag is required")
    val hasMemberProfile: Boolean?
) : Command<Membership>, MembershipIntervalCandidate {
    override val candidateUserId: Long get() = userId
    override val candidateMembershipId: Long? get() = null
    // Self-signup always creates a new active membership starting today.
    override val candidateStartDate: LocalDate get() = LocalDate.now()
    override val candidateEndDate: LocalDate? get() = null
}

@ValidMembership
data class BoardCreateMembershipCommand(
    @field:NotNull(message = "User ID is required")
    val userId: Long?,
    @field:NotNull(message = "Member type is required")
    val memberType: MemberType?,
    @field:NotNull(message = "Start date is required")
    @field:PastOrPresent(message = "Start date cannot be in the future")
    val startDate: LocalDate?,
    @field:PastOrPresent(message = "End date cannot be in the future")
    val endDate: LocalDate?,
    @field:NotNull(message = "Incasso flag is required")
    val incasso: Boolean?
) : Command<Membership>, MembershipIntervalCandidate {
    override val candidateUserId: Long? get() = userId
    override val candidateMembershipId: Long? get() = null
    override val candidateStartDate: LocalDate? get() = startDate
    override val candidateEndDate: LocalDate? get() = endDate
}

@ValidMembership
data class CorrectMembershipCommand(
    @field:NotNull(message = "Membership ID is required")
    val id: Long?,
    @field:NotNull(message = "User ID is required")
    val userId: Long?,
    val memberType: MemberType?,
    @field:NotNull(message = "Start date is required")
    @field:PastOrPresent(message = "Start date cannot be in the future")
    val startDate: LocalDate?,
    @field:PastOrPresent(message = "End date cannot be in the future")
    val endDate: LocalDate?,
    val incasso: Boolean?,
    @field:NotNull(message = "Version is required for optimistic locking")
    val version: Long
) : Command<Membership>, MembershipIntervalCandidate {
    override val candidateUserId: Long? get() = userId
    override val candidateMembershipId: Long? get() = id
    override val candidateStartDate: LocalDate? get() = startDate
    override val candidateEndDate: LocalDate? get() = endDate
}

data class EndMembershipCommand(
    @field:NotNull(message = "Membership ID is required")
    @field:Positive(message = "Membership ID must be positive")
    val id: Long?
) : Command<Membership>

data class ReopenMembershipCommand(
    @field:NotNull(message = "Membership ID is required")
    @field:Positive(message = "Membership ID must be positive")
    val id: Long?
) : Command<Membership>

data class FindMembershipByIdCommand(
    @field:NotNull(message = "Membership ID is required")
    @field:Positive(message = "Membership ID must be positive")
    val id: Long?
) : Command<Membership>

data class DeleteMembershipCommand(
    @field:NotNull(message = "Membership ID is required")
    @field:Positive(message = "Membership ID must be positive")
    val id: Long?
) : Command<Unit>

data class RestoreMembershipCommand(
    @field:NotNull(message = "Membership ID is required")
    @field:Positive(message = "Membership ID must be positive")
    val id: Long?
) : Command<Membership>

data class FindDeletedMembershipsCommand(
    @field:NotNull(message = "User ID is required")
    @field:Positive(message = "User ID must be positive")
    val userId: Long?
) : Command<MutableList<Membership>>

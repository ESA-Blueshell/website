package net.blueshell.api.domain.user.command

import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.PastOrPresent
import jakarta.validation.constraints.Positive
import net.blueshell.api.domain.user.application.query.MembershipQuery
import net.blueshell.api.domain.user.application.validation.MembershipUserIdCandidate
import net.blueshell.api.domain.user.application.validation.NoExistingMembershipForUser
import net.blueshell.api.domain.user.persistence.Membership
import net.blueshell.api.shared.command.Command
import net.blueshell.api.shared.enums.MemberType
import java.time.LocalDate

data class FindMembershipsCommand(
    val filter: MembershipQuery
) : Command<MutableList<Membership>>

@NoExistingMembershipForUser
data class CreateMembershipCommand(
    val userId: Long,
    @field:NotNull(message = "isMember flag is required")
    val isMember: Boolean?,
    @field:NotNull(message = "hasAddress flag is required")
    val hasAddress: Boolean?,
    @field:NotNull(message = "personDetails are required")
    val hasPersonDetails: Boolean?
) : Command<Membership>, MembershipUserIdCandidate {
    override val membershipUserId: Long = userId
}

@NoExistingMembershipForUser
data class BoardCreateMembershipCommand(
    @field:NotNull(message = "User ID is required")
    val userId: Long?,
    @field:NotNull(message = "Member type is required")
    val memberType: MemberType?,
    @field:NotNull(message = "Start date is required")
    @field:PastOrPresent(message = "Start date cannot be in the future")
    val startDate: LocalDate?,
    val endDate: LocalDate?,
    @field:NotNull(message = "Incasso flag is required")
    val incasso: Boolean?
) : Command<Membership>, MembershipUserIdCandidate {
    override val membershipUserId: Long? = userId
}

data class UpdateMembershipCommand(
    @field:NotNull(message = "Membership ID is required")
    val id: Long?,
    @field:NotNull(message = "User ID is required")
    val userId: Long?,
    val memberType: MemberType?,
    @field:NotNull(message = "Start date is required")
    @field:PastOrPresent(message = "Start date cannot be in the future")
    val startDate: LocalDate?,
    val endDate: LocalDate?,
    val incasso: Boolean?,
    @field:NotNull(message = "Version is required for optimistic locking")
    val version: Long
) : Command<Membership>

data class FindMembershipByIdCommand(
    @field:NotNull(message = "Membership ID is required")
    @field:Positive(message = "Membership ID must be positive")
    val id: Long?
) : Command<Membership>

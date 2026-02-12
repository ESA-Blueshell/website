package net.blueshell.api.domain.membership.command

import net.blueshell.api.domain.membership.application.validation.MembershipUserIdCandidate
import net.blueshell.api.domain.membership.application.validation.NoExistingMembershipForUser
import net.blueshell.api.domain.membership.persistence.Membership
import net.blueshell.api.domain.membership.persistence.filter.MembershipFilter
import net.blueshell.api.shared.command.Command
import net.blueshell.api.shared.enums.MemberType
import java.time.LocalDate

data class FindMembershipsCommand(
    val filter: MembershipFilter
) : Command<MutableList<Membership>>

@NoExistingMembershipForUser
data class CreateMembershipCommand(
    val principalId: Long?,
    val isMember: Boolean,
    val hasAddress: Boolean
) : Command<Membership>, MembershipUserIdCandidate {
    override val membershipUserId: Long? = principalId
}

@NoExistingMembershipForUser
data class BoardCreateMembershipCommand(
    val userId: Long,
    val memberType: MemberType,
    val startDate: LocalDate,
    val endDate: LocalDate?,
    val incasso: Boolean
) : Command<Membership>, MembershipUserIdCandidate {
    override val membershipUserId: Long? = userId
}

data class UpdateMembershipCommand(
    val id: Long,
    val userId: Long,
    val memberType: MemberType?,
    val startDate: LocalDate,
    val endDate: LocalDate?,
    val incasso: Boolean?,
    val version: Long?
) : Command<Membership>

data class FindMembershipByIdCommand(
    val id: Long
) : Command<Membership>

package net.blueshell.api.domain.membership.command

import net.blueshell.api.domain.membership.persistence.Membership
import net.blueshell.api.domain.membership.persistence.filter.MembershipFilter
import net.blueshell.api.domain.membership.web.dto.MembershipDTO
import net.blueshell.api.shared.command.Command

data class FindMembershipsCommand(
    val filter: MembershipFilter
) : Command<MutableList<Membership>>

data class CreateMembershipCommand(
    val principalId: Long?,
    val isMember: Boolean,
    val hasAddress: Boolean
) : Command<Membership>

data class BoardCreateMembershipCommand(
    val dto: MembershipDTO
) : Command<Membership>

data class UpdateMembershipCommand(
    val id: Long,
    val dto: MembershipDTO
) : Command<Membership>

data class FindMembershipByIdCommand(
    val id: Long
) : Command<Membership>

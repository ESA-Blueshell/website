package net.blueshell.api.domain.membership.command

import net.blueshell.api.domain.membership.persistence.Membership
import net.blueshell.api.domain.membership.persistence.filter.MembershipFilter
import net.blueshell.api.shared.command.Command
import net.blueshell.api.shared.enums.MemberType
import java.time.LocalDate

data class FindMembershipsCommand(
    val filter: MembershipFilter
) : Command<MutableList<Membership>>

data class CreateMembershipCommand(
    val principalId: Long?,
    val isMember: Boolean,
    val hasAddress: Boolean
) : Command<Membership>

data class BoardCreateMembershipCommand(
    val userId: Long,
    val memberType: MemberType,
    val startDate: LocalDate,
    val endDate: LocalDate?,
    val incasso: Boolean
) : Command<Membership>

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

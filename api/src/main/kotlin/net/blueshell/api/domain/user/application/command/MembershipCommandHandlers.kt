package net.blueshell.api.domain.user.application.command

import net.blueshell.api.domain.user.application.MembershipService
import net.blueshell.api.domain.user.persistence.Membership
import net.blueshell.api.domain.user.application.UserService
import net.blueshell.api.domain.user.command.BoardCreateMembershipCommand
import net.blueshell.api.domain.user.command.CreateMembershipCommand
import net.blueshell.api.domain.user.command.FindMembershipByIdCommand
import net.blueshell.api.domain.user.command.FindMembershipsCommand
import net.blueshell.api.domain.user.command.UpdateMembershipCommand
import net.blueshell.api.shared.command.CommandHandler
import org.springframework.security.access.AccessDeniedException
import org.springframework.stereotype.Component

@Component
class FindMembershipsHandler(
    private val service: MembershipService
) : CommandHandler<FindMembershipsCommand, MutableList<Membership>> {
    override val commandType = FindMembershipsCommand::class

    override fun handle(command: FindMembershipsCommand): MutableList<Membership> {
        return service.findByQuery(command.filter)
    }
}

@Component
class CreateMembershipHandler(
    private val service: MembershipService,
    private val users: UserService
) : CommandHandler<CreateMembershipCommand, Membership> {
    override val commandType = CreateMembershipCommand::class

    override fun handle(command: CreateMembershipCommand): Membership {
        if (command.isMember!!) {
            throw AccessDeniedException("User already has an active membership")
        }
        if (!command.hasAddress!!) {
            throw AccessDeniedException("User must have an address")
        }
        if (!command.hasMemberProfile!!) {
            throw AccessDeniedException("Complete profile is required before applying for membership")
        }
        val membership = Membership(
            user = users.findById(command.userId),
            startDate = java.time.LocalDate.now(),
        )
        service.create(membership)
        return membership
    }
}

@Component
class BoardCreateMembershipHandler(
    private val service: MembershipService,
    private val users: UserService
) : CommandHandler<BoardCreateMembershipCommand, Membership> {
    override val commandType = BoardCreateMembershipCommand::class

    override fun handle(command: BoardCreateMembershipCommand): Membership {
        val membership = Membership(
            user = users.findById(command.userId!!),
            memberType = command.memberType!!,
            startDate = command.startDate!!,
            endDate = command.endDate,
            incasso = command.incasso!!,
        )
        return service.create(membership)
    }
}

@Component
class UpdateMembershipHandler(
    private val service: MembershipService,
    private val users: UserService
) : CommandHandler<UpdateMembershipCommand, Membership> {
    override val commandType = UpdateMembershipCommand::class

    override fun handle(command: UpdateMembershipCommand): Membership {
        var membership = service.findById(command.id!!)
        membership.user = users.findById(command.userId!!)
        command.memberType?.let { membership.memberType = it }
        membership.startDate = command.startDate!!
        membership.endDate = command.endDate
        command.incasso?.let { membership.incasso = it }
        membership.version = command.version
        membership = service.update(membership)
        return membership
    }
}

@Component
class FindMembershipByIdHandler(
    private val service: MembershipService
) : CommandHandler<FindMembershipByIdCommand, Membership> {
    override val commandType = FindMembershipByIdCommand::class

    override fun handle(command: FindMembershipByIdCommand): Membership {
        return service.findById(command.id!!)
    }
}

package net.blueshell.api.domain.membership.application.command

import net.blueshell.api.domain.membership.application.MembershipService
import net.blueshell.api.domain.membership.command.BoardCreateMembershipCommand
import net.blueshell.api.domain.membership.command.CreateMembershipCommand
import net.blueshell.api.domain.membership.command.FindMembershipByIdCommand
import net.blueshell.api.domain.membership.command.FindMembershipsCommand
import net.blueshell.api.domain.membership.command.UpdateMembershipCommand
import net.blueshell.api.domain.membership.persistence.Membership
import net.blueshell.api.domain.membership.web.mapping.asEntity
import net.blueshell.api.domain.user.persistence.User
import net.blueshell.api.shared.command.CommandHandler
import net.blueshell.api.shared.model.asRef
import org.springframework.security.access.AccessDeniedException
import org.springframework.stereotype.Component

@Component
class FindMembershipsHandler(
    private val service: MembershipService
) : CommandHandler<FindMembershipsCommand, MutableList<Membership>> {
    override val commandType = FindMembershipsCommand::class

    override fun handle(command: FindMembershipsCommand): MutableList<Membership> {
        return service.findByFilter(command.filter)
    }
}

@Component
class CreateMembershipHandler(
    private val service: MembershipService
) : CommandHandler<CreateMembershipCommand, Membership> {
    override val commandType = CreateMembershipCommand::class

    override fun handle(command: CreateMembershipCommand): Membership {
        if (command.isMember) {
            throw AccessDeniedException("User is already a member")
        }
        if (!command.hasAddress) {
            throw AccessDeniedException("User must have an address")
        }
        val principalId = requireNotNull(command.principalId) { "User must be authenticated" }

        val membership = Membership()
        membership.user = User::class.asRef(principalId)
        service.create(membership)
        return membership
    }
}

@Component
class BoardCreateMembershipHandler(
    private val service: MembershipService
) : CommandHandler<BoardCreateMembershipCommand, Membership> {
    override val commandType = BoardCreateMembershipCommand::class

    override fun handle(command: BoardCreateMembershipCommand): Membership {
        var membership = command.dto.asEntity()
        membership = service.create(membership)
        return membership
    }
}

@Component
class UpdateMembershipHandler(
    private val service: MembershipService
) : CommandHandler<UpdateMembershipCommand, Membership> {
    override val commandType = UpdateMembershipCommand::class

    override fun handle(command: UpdateMembershipCommand): Membership {
        var membership = service.findById(command.id)
        command.dto.asEntity(membership)
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
        return service.findById(command.id)
    }
}

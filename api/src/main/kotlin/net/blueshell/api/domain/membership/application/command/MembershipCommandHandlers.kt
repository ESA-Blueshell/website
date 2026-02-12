package net.blueshell.api.domain.membership.application.command

import net.blueshell.api.domain.membership.application.MembershipService
import net.blueshell.api.domain.membership.command.*
import net.blueshell.api.domain.membership.persistence.Membership
import net.blueshell.api.domain.user.application.UserService
import net.blueshell.api.shared.command.CommandHandler
import net.blueshell.api.shared.validation.DatabaseValidationErrors
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
    private val service: MembershipService,
    private val users: UserService
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

        validateMembershipCreation(CreateMembershipCommand::class.simpleName ?: "CreateMembershipCommand", principalId, service)

        val membership = Membership()
        membership.user = users.findById(principalId)
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
        validateMembershipCreation(
            BoardCreateMembershipCommand::class.simpleName ?: "BoardCreateMembershipCommand",
            command.userId,
            service
        )
        var membership = Membership()
        membership.user = users.findById(command.userId)
        membership.memberType = command.memberType
        membership.startDate = command.startDate
        membership.endDate = command.endDate
        membership.incasso = command.incasso
        membership = service.create(membership)
        return membership
    }
}

@Component
class UpdateMembershipHandler(
    private val service: MembershipService,
    private val users: UserService
) : CommandHandler<UpdateMembershipCommand, Membership> {
    override val commandType = UpdateMembershipCommand::class

    override fun handle(command: UpdateMembershipCommand): Membership {
        var membership = service.findById(command.id)
        membership.user = users.findById(command.userId)
        command.memberType?.let { membership.memberType = it }
        command.startDate?.let { membership.startDate = it }
        membership.endDate = command.endDate
        command.incasso?.let { membership.incasso = it }
        command.version?.let { membership.version = it }
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

private fun validateMembershipCreation(objectName: String, userId: Long, memberships: MembershipService) {
    val errors = DatabaseValidationErrors(objectName)
    if (memberships.existsByUserId(userId)) {
        errors.reject("userId", userId, "User is already a member.", "NoExistingMembershipForUserId")
    }
    errors.throwIfAny()
}

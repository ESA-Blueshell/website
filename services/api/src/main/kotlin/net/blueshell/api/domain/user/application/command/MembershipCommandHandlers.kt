package net.blueshell.api.domain.user.application.command

import net.blueshell.api.domain.user.application.MembershipService
import net.blueshell.api.domain.user.application.UserService
import net.blueshell.api.domain.user.application.exception.InvalidMembershipException
import net.blueshell.api.domain.user.application.validation.MembershipInvariants
import net.blueshell.api.domain.user.persistence.Membership
import net.blueshell.api.domain.user.command.BoardCreateMembershipCommand
import net.blueshell.api.domain.user.command.CorrectMembershipCommand
import net.blueshell.api.domain.user.command.CreateMembershipCommand
import net.blueshell.api.domain.user.command.EndMembershipCommand
import net.blueshell.api.domain.user.command.FindMembershipByIdCommand
import net.blueshell.api.domain.user.command.FindMembershipsCommand
import net.blueshell.api.domain.user.command.ReopenMembershipCommand
import net.blueshell.api.shared.command.CommandHandler
import org.springframework.security.access.AccessDeniedException
import org.springframework.stereotype.Component
import java.time.LocalDate

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
    private val users: UserService,
    private val invariants: MembershipInvariants
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
        val startDate = LocalDate.now()
        invariants.validate(command.userId, null, startDate, null)
        val membership = Membership(
            user = users.findById(command.userId),
            startDate = startDate,
        )
        return service.create(membership)
    }
}

@Component
class BoardCreateMembershipHandler(
    private val service: MembershipService,
    private val users: UserService,
    private val invariants: MembershipInvariants
) : CommandHandler<BoardCreateMembershipCommand, Membership> {
    override val commandType = BoardCreateMembershipCommand::class

    override fun handle(command: BoardCreateMembershipCommand): Membership {
        invariants.validate(command.userId!!, null, command.startDate!!, command.endDate)
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
class CorrectMembershipHandler(
    private val service: MembershipService,
    private val invariants: MembershipInvariants
) : CommandHandler<CorrectMembershipCommand, Membership> {
    override val commandType = CorrectMembershipCommand::class

    override fun handle(command: CorrectMembershipCommand): Membership {
        val membership = service.findById(command.id!!)
        invariants.validate(membership.userId, membership.id, command.startDate!!, command.endDate)
        command.memberType?.let { membership.memberType = it }
        membership.startDate = command.startDate!!
        membership.endDate = command.endDate
        command.incasso?.let { membership.incasso = it }
        membership.version = command.version
        return service.update(membership)
    }
}

@Component
class EndMembershipHandler(
    private val service: MembershipService
) : CommandHandler<EndMembershipCommand, Membership> {
    override val commandType = EndMembershipCommand::class

    override fun handle(command: EndMembershipCommand): Membership {
        val membership = service.findById(command.id!!)
        if (membership.endDate != null) {
            throw InvalidMembershipException("Membership is already ended")
        }
        if (membership.startDate == LocalDate.now()) {
            throw InvalidMembershipException(
                "A membership that started today cannot be ended; delete it instead."
            )
        }
        membership.endDate = LocalDate.now()
        return service.update(membership)
    }
}

@Component
class ReopenMembershipHandler(
    private val service: MembershipService,
    private val invariants: MembershipInvariants
) : CommandHandler<ReopenMembershipCommand, Membership> {
    override val commandType = ReopenMembershipCommand::class

    override fun handle(command: ReopenMembershipCommand): Membership {
        val membership = service.findById(command.id!!)
        if (membership.endDate == null) {
            throw InvalidMembershipException("Membership is already active")
        }
        invariants.validate(membership.userId, membership.id, membership.startDate, null)
        membership.endDate = null
        return service.update(membership)
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

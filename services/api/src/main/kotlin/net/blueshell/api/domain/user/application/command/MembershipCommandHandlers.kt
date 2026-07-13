package net.blueshell.api.domain.user.application.command

import jakarta.validation.ConstraintViolationException
import jakarta.validation.Validator
import net.blueshell.api.domain.user.application.MembershipService
import net.blueshell.api.domain.user.application.UserService
import net.blueshell.api.domain.user.command.BoardCreateMembershipCommand
import net.blueshell.api.domain.user.command.CorrectMembershipCommand
import net.blueshell.api.domain.user.command.CreateMembershipCommand
import net.blueshell.api.domain.user.command.EndMembershipCommand
import net.blueshell.api.domain.user.command.FindMembershipByIdCommand
import net.blueshell.api.domain.user.command.FindMembershipsCommand
import net.blueshell.api.domain.user.command.ReopenMembershipCommand
import net.blueshell.api.domain.user.persistence.Membership
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
            startDate = LocalDate.now(),
        )
        return service.create(membership)
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
class CorrectMembershipHandler(
    private val service: MembershipService
) : CommandHandler<CorrectMembershipCommand, Membership> {
    override val commandType = CorrectMembershipCommand::class

    override fun handle(command: CorrectMembershipCommand): Membership {
        val membership = service.findById(command.id!!)
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
    private val service: MembershipService,
    private val validator: Validator
) : CommandHandler<EndMembershipCommand, Membership> {
    override val commandType = EndMembershipCommand::class

    override fun handle(command: EndMembershipCommand): Membership {
        val membership = service.findById(command.id!!)
        // Ending stamps today; validating the resulting interval reuses the
        // membership constraints, so a membership that started today (zero-day
        // span) or would overlap another is rejected with consistent errors.
        validateInterval(membership, LocalDate.now())
        membership.endDate = LocalDate.now()
        return service.update(membership)
    }

    private fun validateInterval(membership: Membership, endDate: LocalDate?) {
        val violations = validator.validate(membership.asCorrection(endDate))
        if (violations.isNotEmpty()) throw ConstraintViolationException(violations)
    }
}

@Component
class ReopenMembershipHandler(
    private val service: MembershipService,
    private val validator: Validator
) : CommandHandler<ReopenMembershipCommand, Membership> {
    override val commandType = ReopenMembershipCommand::class

    override fun handle(command: ReopenMembershipCommand): Membership {
        val membership = service.findById(command.id!!)
        // Reopening clears the end date; validate the resulting open interval so
        // another active membership or an overlap is rejected consistently.
        val violations = validator.validate(membership.asCorrection(null))
        if (violations.isNotEmpty()) throw ConstraintViolationException(violations)
        membership.endDate = null
        return service.update(membership)
    }
}

/**
 * Represents the state a lifecycle operation would leave the membership in, as a
 * [CorrectMembershipCommand], so it can be run through the shared membership
 * constraints with an inline `Validator.validate` call.
 */
private fun Membership.asCorrection(endDate: LocalDate?): CorrectMembershipCommand =
    CorrectMembershipCommand(
        id = this.id,
        userId = this.userId,
        memberType = this.memberType,
        startDate = this.startDate,
        endDate = endDate,
        incasso = this.incasso,
        version = this.version,
    )

@Component
class FindMembershipByIdHandler(
    private val service: MembershipService
) : CommandHandler<FindMembershipByIdCommand, Membership> {
    override val commandType = FindMembershipByIdCommand::class

    override fun handle(command: FindMembershipByIdCommand): Membership {
        return service.findById(command.id!!)
    }
}

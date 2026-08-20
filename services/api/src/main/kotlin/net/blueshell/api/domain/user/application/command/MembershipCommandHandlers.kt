package net.blueshell.api.domain.user.application.command

import org.springframework.transaction.annotation.Transactional
import net.blueshell.api.domain.user.persistence.Address
import net.blueshell.api.domain.user.command.SubmitSignupApplicationCommand
import net.blueshell.api.domain.user.command.SaveSignupAddressCommand
import net.blueshell.api.domain.auth.application.SignupTokenService
import jakarta.validation.ConstraintViolationException
import jakarta.validation.Validator
import net.blueshell.api.domain.user.application.MembershipService
import net.blueshell.api.domain.user.application.UserService
import net.blueshell.api.domain.user.application.exception.MembershipNotFoundException
import net.blueshell.api.domain.user.command.BoardCreateMembershipCommand
import net.blueshell.api.domain.user.command.CorrectMembershipCommand
import net.blueshell.api.domain.auth.application.SignupCompletionService
import net.blueshell.api.shared.model.SignupOutcome
import net.blueshell.api.domain.user.application.MemberProfileService
import net.blueshell.api.domain.user.command.SubmitMembershipApplicationCommand
import net.blueshell.api.domain.user.command.DeleteMembershipCommand
import net.blueshell.api.domain.user.command.EndMembershipCommand
import net.blueshell.api.domain.user.command.FindDeletedMembershipsCommand
import net.blueshell.api.domain.user.command.FindMembershipByIdCommand
import net.blueshell.api.domain.user.command.FindMembershipsCommand
import net.blueshell.api.domain.user.command.ReopenMembershipCommand
import net.blueshell.api.domain.user.command.RestoreMembershipCommand
import net.blueshell.api.domain.user.persistence.Membership
import net.blueshell.api.shared.command.CommandHandler
import org.springframework.security.access.AccessDeniedException
import org.springframework.stereotype.Component
import java.time.Instant
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
class SubmitMembershipApplicationHandler(
    private val users: UserService,
    private val memberProfiles: MemberProfileService,
    private val completion: SignupCompletionService
) : CommandHandler<SubmitMembershipApplicationCommand, SignupOutcome> {
    override val commandType = SubmitMembershipApplicationCommand::class

    override fun handle(command: SubmitMembershipApplicationCommand): SignupOutcome {
        val profile = users.findById(command.userId).memberProfile
            ?: throw AccessDeniedException("Complete profile is required before applying for membership")
        profile.conditionsAcceptedAt = Instant.now()
        memberProfiles.update(profile)

        val outcome = completion.completeIfReady(command.userId)
        // An explicit application that cannot commit is a refusal, unlike the same
        // call from email confirmation, where not-yet-ready is the normal case.
        if (!outcome.membershipStarted) {
            throw AccessDeniedException("Membership application is not complete")
        }
        return outcome
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

@Component
class DeleteMembershipHandler(
    private val service: MembershipService
) : CommandHandler<DeleteMembershipCommand, Unit> {
    override val commandType = DeleteMembershipCommand::class

    override fun handle(command: DeleteMembershipCommand) {
        service.deleteById(command.id!!)
    }
}

@Component
class FindDeletedMembershipsHandler(
    private val service: MembershipService
) : CommandHandler<FindDeletedMembershipsCommand, MutableList<Membership>> {
    override val commandType = FindDeletedMembershipsCommand::class

    override fun handle(command: FindDeletedMembershipsCommand): MutableList<Membership> =
        service.findDeletedByUserId(command.userId!!)
}

@Component
class RestoreMembershipHandler(
    private val service: MembershipService,
    private val validator: Validator
) : CommandHandler<RestoreMembershipCommand, Membership> {
    override val commandType = RestoreMembershipCommand::class

    override fun handle(command: RestoreMembershipCommand): Membership {
        val deleted = service.findDeletedById(command.id!!)
            ?: throw MembershipNotFoundException(command.id)
        // Validate the interval it would return to, against the user's live memberships,
        // reusing @ValidMembership (one active max, no overlap, start<end) for consistent errors.
        val target = CorrectMembershipCommand(
            id = deleted.id,
            userId = deleted.userId,
            memberType = deleted.memberType,
            startDate = deleted.startDate,
            endDate = deleted.endDate,
            incasso = deleted.incasso,
            version = deleted.version,
        )
        val violations = validator.validate(target)
        if (violations.isNotEmpty()) throw ConstraintViolationException(violations)
        return service.restore(deleted)
    }
}

@Component
class SaveSignupAddressHandler(
    private val users: UserService,
    private val signupTokens: SignupTokenService
) : CommandHandler<SaveSignupAddressCommand, Unit> {
    override val commandType = SaveSignupAddressCommand::class

    // Transactional so the account resolved from the token stays managed: without
    // it the read closes its own transaction and the entity comes back detached.
    @Transactional
    override fun handle(command: SaveSignupAddressCommand) {
        val user = signupTokens.resolveAccount(command.signupToken).user
        // replaceAddress is an upsert, so going back a step and correcting the
        // address works without the client tracking an id.
        user.replaceAddress(
            Address(
                user = user,
                country = command.country,
                city = command.city,
                street = command.street,
                houseNumber = command.houseNumber,
                zipCode = command.zipCode,
            )
        )
        users.update(user)
    }
}

@Component
class SubmitSignupApplicationHandler(
    private val memberProfiles: MemberProfileService,
    private val signupTokens: SignupTokenService,
    private val completion: SignupCompletionService
) : CommandHandler<SubmitSignupApplicationCommand, SignupOutcome> {
    override val commandType = SubmitSignupApplicationCommand::class

    @Transactional
    override fun handle(command: SubmitSignupApplicationCommand): SignupOutcome {
        val account = signupTokens.resolveAccount(command.signupToken)
        val profile = account.user.memberProfile
            ?: throw AccessDeniedException("This signup did not apply for membership")
        profile.conditionsAcceptedAt = Instant.now()
        memberProfiles.update(profile)

        // Unlike the signed-in route, a non-commit here is the normal case: the
        // email address may not be confirmed yet.
        return completion.completeIfReady(account.id)
    }
}

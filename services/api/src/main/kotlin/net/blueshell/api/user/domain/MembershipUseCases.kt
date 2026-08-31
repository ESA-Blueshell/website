package net.blueshell.api.user.domain

import jakarta.validation.ConstraintViolationException
import jakarta.validation.Validator
import net.blueshell.api.user.persistence.Membership
import net.blueshell.api.shared.enums.MemberType
import net.blueshell.api.shared.model.SignupOutcome
import org.springframework.security.access.AccessDeniedException
import org.springframework.stereotype.Service
import java.time.Instant
import java.time.LocalDate
import net.blueshell.api.user.api.MemberProfileService
import net.blueshell.api.user.api.MembershipService
import net.blueshell.api.user.api.SignupCompletion
import net.blueshell.api.user.api.UserService

/**
 * Membership writes. Every one of them ends up asserting the same interval
 * invariants, so each builds a [MembershipInterval] and runs it through the
 * shared constraints before touching the entity.
 */
@Service
class MembershipUseCases(
    private val service: MembershipService,
    private val users: UserService,
    private val memberProfiles: MemberProfileService,
    private val completion: SignupCompletion,
    private val validator: Validator,
) {
    fun findByQuery(filter: MembershipQuery): MutableList<Membership> = service.findByQuery(filter)

    fun findById(id: Long): Membership = service.findById(id)

    fun findDeletedByUserId(userId: Long): MutableList<Membership> = service.findDeletedByUserId(userId)

    fun delete(id: Long) = service.deleteById(id)

    fun boardCreate(
        userId: Long,
        memberType: MemberType,
        startDate: LocalDate?,
        endDate: LocalDate?,
        incasso: Boolean,
    ): Membership {
        validate(MembershipInterval(userId = userId, startDate = startDate, endDate = endDate))
        val membership = Membership(
            user = users.findById(userId),
            memberType = memberType,
            startDate = startDate!!,
            endDate = endDate,
            incasso = incasso,
        )
        return service.create(membership)
    }

    fun correct(
        id: Long,
        userId: Long,
        memberType: MemberType?,
        startDate: LocalDate?,
        endDate: LocalDate?,
        incasso: Boolean?,
        version: Long,
    ): Membership {
        validate(MembershipInterval(userId = userId, id = id, startDate = startDate, endDate = endDate))
        val membership = service.findById(id)
        memberType?.let { membership.memberType = it }
        membership.startDate = startDate!!
        membership.endDate = endDate
        incasso?.let { membership.incasso = it }
        membership.version = version
        return service.update(membership)
    }

    /**
     * Ending stamps today. Validating the resulting interval reuses the
     * membership constraints, so a membership that started today (zero-day span)
     * or would overlap another is rejected with consistent errors.
     *
     * [effectiveDate] is the day the membership stops. A bulk end pins one date for the
     * whole selection and passes it here, so a batch that runs across midnight ends every
     * membership on the same day rather than splitting over two.
     */
    fun end(id: Long, effectiveDate: LocalDate = LocalDate.now()): Membership {
        val membership = service.findById(id)
        validate(membership.intervalEndingOn(effectiveDate))
        membership.endDate = effectiveDate
        return service.update(membership)
    }

    /** Reopening clears the end date; the open interval is validated the same way. */
    fun reopen(id: Long): Membership {
        val membership = service.findById(id)
        validate(membership.intervalEndingOn(null))
        membership.endDate = null
        return service.update(membership)
    }

    fun restore(id: Long): Membership {
        val deleted = service.findDeletedById(id) ?: throw MembershipNotFoundException(id)
        // Validate the interval it would return to, against the user's live
        // memberships, so a restore cannot reintroduce an overlap.
        validate(deleted.intervalEndingOn(deleted.endDate))
        return service.restore(deleted)
    }

    /**
     * An explicit application that cannot commit is a refusal, unlike the same
     * call from email confirmation, where not-yet-ready is the normal case.
     */
    fun apply(userId: Long): SignupOutcome {
        validate(MembershipInterval(userId = userId, startDate = LocalDate.now()))
        val profile = users.findById(userId).memberProfile
            ?: throw AccessDeniedException("Complete profile is required before applying for membership")
        profile.conditionsAcceptedAt = Instant.now()
        memberProfiles.update(profile)

        val outcome = completion.completeIfReady(userId)
        if (!outcome.membershipStarted) {
            throw AccessDeniedException("Membership application is not complete")
        }
        return outcome
    }

    private fun validate(interval: MembershipInterval) {
        val violations = validator.validate(interval)
        if (violations.isNotEmpty()) throw ConstraintViolationException(violations)
    }
}

private fun Membership.intervalEndingOn(endDate: LocalDate?): MembershipInterval =
    MembershipInterval(userId = this.userId, id = this.id, startDate = this.startDate, endDate = endDate)

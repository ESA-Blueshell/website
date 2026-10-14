package net.blueshell.api.user.domain

import jakarta.validation.ConstraintViolationException
import jakarta.validation.Validator
import net.blueshell.api.shared.enums.MemberType
import net.blueshell.api.shared.model.SignupOutcome
import net.blueshell.api.user.api.MemberProfileService
import net.blueshell.api.user.api.MembershipConditions
import net.blueshell.api.user.api.MembershipService
import net.blueshell.api.user.api.SignupCompletion
import net.blueshell.api.user.api.UserService
import net.blueshell.api.user.persistence.Membership
import net.blueshell.api.user.persistence.MembershipType
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.time.Instant
import java.time.LocalDate

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
    ): Membership {
        validate(MembershipInterval(userId = userId, startDate = startDate, endDate = endDate))
        val membership =
            Membership(
                user = users.findById(userId),
                memberType = memberType,
                startDate = startDate!!,
                endDate = endDate,
            )
        return service.create(membership)
    }

    /** Corrects the dates; the first type moves with the start, and every later type must still fit. */
    @Transactional
    fun correct(
        id: Long,
        userId: Long,
        startDate: LocalDate?,
        endDate: LocalDate?,
        version: Long,
    ): Membership {
        val membership = service.findById(id)
        membership.requireVersion(version)
        validate(MembershipInterval(userId = userId, id = id, startDate = startDate, endDate = endDate))
        val start = startDate!!
        if (membership.types.drop(1).any { it.since == start || !runsOn(it.since, start, endDate) }) {
            throw MembershipTypeOutsideMembership(start, endDate)
        }
        membership.types.firstOrNull()?.let { membership.correctType(it, it.type, start) }
        membership.startDate = start
        membership.endDate = endDate
        return service.update(membership)
    }

    @Transactional
    fun recordType(
        id: Long,
        type: MemberType,
        since: LocalDate,
    ): Membership {
        val membership = service.findById(id)
        requireFreeDay(membership, since, except = null)
        membership.recordType(type, since)
        return service.update(membership.activatedIfHonorary(type, since))
    }

    /** The first type takes another kind only: it starts with the membership, so its day is the start. */
    @Transactional
    fun correctType(
        id: Long,
        typeId: Long,
        type: MemberType,
        since: LocalDate,
    ): Membership {
        val membership = service.findById(id)
        val entry = membership.typeById(typeId)
        if (entry == membership.types.first()) {
            if (since != membership.startDate) throw FirstMembershipTypeStartsTheMembership()
        } else {
            requireFreeDay(membership, since, except = entry)
        }
        membership.correctType(entry, type, since)
        return service.update(membership.activatedIfHonorary(type, since))
    }

    @Transactional
    fun removeType(
        id: Long,
        typeId: Long,
    ): Membership {
        val membership = service.findById(id)
        val entry = membership.typeById(typeId)
        if (entry == membership.types.first()) throw FirstMembershipTypeStartsTheMembership()
        membership.removeType(entry)
        return service.update(membership)
    }

    private fun requireFreeDay(
        membership: Membership,
        since: LocalDate,
        except: MembershipType?,
    ) {
        if (!runsOn(since, membership.startDate, membership.endDate)) {
            throw MembershipTypeOutsideMembership(membership.startDate, membership.endDate)
        }
        if (membership.types.any { it != except && it.since == since }) throw MembershipTypeOnTakenDay(since)
    }

    // A set end date is the day the membership stopped, so a type cannot start on it. TWIN: the
    // day bounds in `MembershipTypeFields.vue`.
    private fun runsOn(
        day: LocalDate,
        startDate: LocalDate,
        endDate: LocalDate?,
    ): Boolean = !day.isBefore(startDate) && (endDate == null || day.isBefore(endDate))

    /**
     * [effectiveDate] is the day the membership stops, pinned by a bulk end so a batch running
     * across midnight does not split over two days. The resulting interval goes through the
     * membership constraints, so a zero-day span or an overlap is refused the usual way.
     */
    fun end(
        id: Long,
        effectiveDate: LocalDate = LocalDate.now(),
    ): Membership {
        val membership = service.findById(id)
        validate(membership.intervalEndingOn(effectiveDate))
        if (membership.types.any { !it.since.isBefore(effectiveDate) }) {
            throw MembershipTypeOutsideMembership(membership.startDate, effectiveDate)
        }
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
    fun apply(
        userId: Long,
        conditionsAccepted: Boolean,
    ): SignupOutcome {
        MembershipConditions.requireAccepted(conditionsAccepted)
        validate(MembershipInterval(userId = userId, startDate = LocalDate.now()))
        val profile =
            users.findById(userId).memberProfile
                ?: throw ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Complete profile is required before applying for membership",
                )
        profile.conditionsAcceptedAt = Instant.now()
        memberProfiles.update(profile)

        val outcome = completion.completeIfReady(userId)
        if (!outcome.membershipStarted) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "Membership application is not complete")
        }
        return outcome
    }

    private fun validate(interval: MembershipInterval) {
        val violations = validator.validate(interval)
        if (violations.isNotEmpty()) throw ConstraintViolationException(violations)
    }
}

private fun Membership.typeById(typeId: Long): MembershipType =
    types.firstOrNull { it.id == typeId } ?: throw MembershipTypeNotFound(typeId)

// Honorary owes nothing, so a pending membership is active from the day it became honorary (api ADR-036).
private fun Membership.activatedIfHonorary(
    type: MemberType,
    since: LocalDate,
): Membership = apply { if (type == MemberType.HONORARY && activatedOn == null) activatedOn = since }

private fun Membership.intervalEndingOn(endDate: LocalDate?): MembershipInterval =
    MembershipInterval(userId = this.userId, id = this.id, startDate = this.startDate, endDate = endDate)

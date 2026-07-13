package net.blueshell.api.domain.user.application.validation

import net.blueshell.api.domain.user.application.exception.InvalidMembershipException
import net.blueshell.api.domain.user.persistence.repository.MemberRepository
import org.springframework.stereotype.Component
import java.time.LocalDate

/**
 * Enforces the cross-field and cross-entity membership invariants that bean
 * validation on the commands cannot express. Single-field constraints (dates
 * not in the future) live on the command DTOs as `@PastOrPresent`.
 *
 * Invariants enforced here, per user:
 * - a membership spans at least one day (`startDate < endDate` when ended);
 * - at most one active membership (`endDate == null`);
 * - no overlapping intervals.
 */
@Component
class MembershipInvariants(
    private val repository: MemberRepository
) {
    fun validate(userId: Long, membershipIdOrNull: Long?, startDate: LocalDate, endDate: LocalDate?) {
        if (endDate != null && !startDate.isBefore(endDate)) {
            throw InvalidMembershipException("Start date must be before end date")
        }

        val others = repository.findByUser_Id(userId).filter { it.id != membershipIdOrNull }

        if (endDate == null && others.any { it.endDate == null }) {
            throw InvalidMembershipException("User already has an active membership")
        }

        if (others.any { intervalsOverlap(startDate, endDate, it.startDate, it.endDate) }) {
            throw InvalidMembershipException("Membership interval overlaps with an existing membership")
        }
    }

    /**
     * Half-open interval overlap: end dates are exclusive, matching the rest of
     * the application, where a set `endDate` marks the day membership stopped.
     * Two intervals [s1, e1) and [s2, e2) overlap iff s1 < e2 and s2 < e1, with a
     * null end treated as +infinity.
     */
    private fun intervalsOverlap(s1: LocalDate, e1: LocalDate?, s2: LocalDate, e2: LocalDate?): Boolean {
        val e1Effective = e1 ?: LocalDate.MAX
        val e2Effective = e2 ?: LocalDate.MAX
        return s1.isBefore(e2Effective) && s2.isBefore(e1Effective)
    }
}

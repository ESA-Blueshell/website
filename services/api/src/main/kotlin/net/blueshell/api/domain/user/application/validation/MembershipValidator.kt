package net.blueshell.api.domain.user.application.validation

import jakarta.validation.ConstraintValidator
import jakarta.validation.ConstraintValidatorContext
import net.blueshell.api.domain.user.persistence.repository.MemberRepository
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import java.time.LocalDate

@Component
class MembershipValidator @Autowired constructor(
    private val repository: MemberRepository
) : ConstraintValidator<ValidMembership, MembershipIntervalCandidate> {

    override fun isValid(candidate: MembershipIntervalCandidate?, context: ConstraintValidatorContext): Boolean {
        candidate ?: return true
        val start = candidate.candidateStartDate ?: return true // let @NotNull report a missing start date
        val end = candidate.candidateEndDate

        context.disableDefaultConstraintViolation()

        // Structural: a closed interval must span at least one day.
        if (end != null && !start.isBefore(end)) {
            context.addViolation("Start date must be before end date", "endDate")
            return false
        }

        // When editing an existing membership, validate against its real owner
        // rather than a userId the client supplied, so a membership cannot be
        // checked against — or moved to — the wrong user.
        val userId = candidate.candidateMembershipId
            ?.let { repository.findById(it).orElse(null)?.userId }
            ?: candidate.candidateUserId
            ?: return true // let @NotNull report a missing user

        val others = repository.findByUser_Id(userId)
            .filter { it.id != candidate.candidateMembershipId }

        var valid = true
        if (end == null && others.any { it.endDate == null }) {
            context.addViolation("User already has an active membership", "endDate")
            valid = false
        }
        if (others.any { overlaps(start, end, it.startDate, it.endDate) }) {
            context.addViolation("Membership interval overlaps with an existing membership", "startDate")
            valid = false
        }
        return valid
    }

    /**
     * Half-open interval overlap: end dates are exclusive, matching the rest of
     * the application where a set `endDate` marks the day membership stopped.
     * Two intervals [s1, e1) and [s2, e2) overlap iff s1 < e2 and s2 < e1, with a
     * null end treated as +infinity.
     */
    private fun overlaps(s1: LocalDate, e1: LocalDate?, s2: LocalDate, e2: LocalDate?): Boolean {
        val e1Effective = e1 ?: LocalDate.MAX
        val e2Effective = e2 ?: LocalDate.MAX
        return s1.isBefore(e2Effective) && s2.isBefore(e1Effective)
    }

    private fun ConstraintValidatorContext.addViolation(message: String, property: String) {
        buildConstraintViolationWithTemplate(message)
            .addPropertyNode(property)
            .addConstraintViolation()
    }
}

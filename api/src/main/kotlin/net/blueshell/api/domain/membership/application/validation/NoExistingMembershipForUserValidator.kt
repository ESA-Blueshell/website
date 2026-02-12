package net.blueshell.api.domain.membership.application.validation

import jakarta.validation.ConstraintValidator
import jakarta.validation.ConstraintValidatorContext
import net.blueshell.api.domain.membership.application.MembershipService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Component
class NoExistingMembershipForUserValidator @Autowired constructor(
    private val memberships: MembershipService
) : ConstraintValidator<NoExistingMembershipForUser, MembershipUserIdCandidate> {
    override fun isValid(candidate: MembershipUserIdCandidate?, context: ConstraintValidatorContext): Boolean {
        val userId = candidate?.membershipUserId ?: return true
        return !memberships.existsByUserId(userId)
    }
}

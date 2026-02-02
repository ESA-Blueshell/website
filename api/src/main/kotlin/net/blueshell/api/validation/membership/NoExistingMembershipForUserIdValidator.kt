package net.blueshell.api.validation.membership

import jakarta.validation.ConstraintValidator
import jakarta.validation.ConstraintValidatorContext
import lombok.extern.slf4j.Slf4j
import net.blueshell.api.service.MembershipService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

/**
 * Validator to check if the username is unique.
 */
@Component
@Slf4j
class NoExistingMembershipForUserIdValidator @Autowired constructor(private val members: MembershipService) :
    ConstraintValidator<NoExistingMembershipForUserId?, Long?> {
    override fun isValid(userId: Long?, context: ConstraintValidatorContext?): Boolean {
        if (userId == null) return true

        return !members.existsByUserId(userId)
    }
}

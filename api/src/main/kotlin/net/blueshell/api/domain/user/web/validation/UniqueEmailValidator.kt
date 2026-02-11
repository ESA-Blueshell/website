package net.blueshell.api.domain.user.web.validation

import jakarta.validation.ConstraintValidator
import jakarta.validation.ConstraintValidatorContext
import net.blueshell.api.user.application.UserService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

/**
 * Validator to check if the email is unique.
 */
@Component
class UniqueEmailValidator @Autowired constructor(private val userService: UserService) :
    ConstraintValidator<UniqueEmail?, String?> {
    override fun isValid(email: String?, context: ConstraintValidatorContext?): Boolean {
        if (email.isNullOrBlank()) {
            // Let @NotBlank or @Email handle this
            return true
        }
        return !userService.existsByEmail(email)
    }
}

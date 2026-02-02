package net.blueshell.api.validation.user

import jakarta.validation.ConstraintValidator
import jakarta.validation.ConstraintValidatorContext
import net.blueshell.api.service.UserService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import org.springframework.util.StringUtils

/**
 * Validator to check if the email is unique.
 */
@Component
class UniqueEmailValidator @Autowired constructor(private val userService: UserService) :
    ConstraintValidator<UniqueEmail?, String?> {
    override fun isValid(email: String?, context: ConstraintValidatorContext?): Boolean {
        if (!StringUtils.hasText(email)) {
            // Let @NotBlank or @Email handle this
            return true
        }
        return !userService.existsByEmail(email)
    }
}

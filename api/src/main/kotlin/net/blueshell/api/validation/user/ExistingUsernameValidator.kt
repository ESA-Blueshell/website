package net.blueshell.api.validation.user

import jakarta.validation.ConstraintValidator
import jakarta.validation.ConstraintValidatorContext
import net.blueshell.api.service.UserService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import org.springframework.util.StringUtils

/**
 * Validator to check if the username is unique.
 */
@Component
class ExistingUsernameValidator @Autowired constructor(private val userService: UserService) :
    ConstraintValidator<ExistingUsername?, String?> {
    override fun isValid(username: String?, context: ConstraintValidatorContext?): Boolean {
        if (!StringUtils.hasText(username)) {
            // Let @NotBlank handle this
            return true
        }
        return userService.existsByUsername(username)
    }
}

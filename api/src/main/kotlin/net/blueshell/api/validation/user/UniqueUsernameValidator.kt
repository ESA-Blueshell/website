package net.blueshell.api.validation.user

import jakarta.validation.ConstraintValidator
import jakarta.validation.ConstraintValidatorContext
import net.blueshell.api.service.UserService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

/**
 * Validator to check if the username is unique.
 */
@Component
class UniqueUsernameValidator @Autowired constructor(private val userService: UserService) :
    ConstraintValidator<net.blueshell.api.validation.user.UniqueUsername?, String?> {
    override fun isValid(username: String?, context: ConstraintValidatorContext?): Boolean {
        if (username.isNullOrEmpty()) {
            // Let @NotBlank handle this
            return true
        }
        return !userService.existsByUsername(username)
    }
}

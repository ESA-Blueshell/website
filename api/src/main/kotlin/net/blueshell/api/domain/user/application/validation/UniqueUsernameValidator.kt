package net.blueshell.api.domain.user.application.validation

import jakarta.validation.ConstraintValidator
import jakarta.validation.ConstraintValidatorContext
import net.blueshell.api.domain.user.application.UserService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Component
class UniqueUsernameValidator @Autowired constructor(
    private val userService: UserService
) : ConstraintValidator<UniqueUsername, String?> {
    override fun isValid(username: String?, context: ConstraintValidatorContext): Boolean {
        if (username.isNullOrBlank()) {
            return true
        }
        return !userService.existsByUsername(username)
    }
}

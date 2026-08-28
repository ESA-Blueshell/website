package net.blueshell.api.user.domain

import jakarta.validation.ConstraintValidator
import jakarta.validation.ConstraintValidatorContext
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import net.blueshell.api.user.api.UserService

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

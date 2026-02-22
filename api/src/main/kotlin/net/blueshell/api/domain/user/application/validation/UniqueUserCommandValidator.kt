package net.blueshell.api.domain.user.application.validation

import jakarta.validation.ConstraintValidator
import jakarta.validation.ConstraintValidatorContext
import net.blueshell.api.domain.user.application.UserService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Component
class UniqueUserCommandValidator @Autowired constructor(
    private val users: UserService
) : ConstraintValidator<UniqueUserCommand, UserUniquenessCandidate> {
    override fun isValid(candidate: UserUniquenessCandidate?, context: ConstraintValidatorContext): Boolean {
        if (candidate == null) return true

        var valid = true
        val currentUserId = candidate.subjectId

        val addViolation = { property: String, message: String ->
            context.disableDefaultConstraintViolation()
            context.buildConstraintViolationWithTemplate(message)
                .addPropertyNode(property)
                .addConstraintViolation()
        }

        val username = candidate.username
        if (!username.isNullOrBlank()) {
            val taken = if (currentUserId == null)
                users.existsByUsername(username)
            else
                users.existsByUsernameAndIdNot(username, currentUserId)
            if (taken) {
                valid = false
                addViolation("username", "Username is taken.")
            }
        }

        val email = candidate.email
        if (!email.isNullOrBlank()) {
            val taken = if (currentUserId == null)
                users.existsByEmail(email)
            else
                users.existsByEmailAndIdNot(email, currentUserId)
            if (taken) {
                valid = false
                addViolation("email", "Email is taken.")
            }
        }

        val discord = candidate.discord
        if (!discord.isNullOrBlank()) {
            val taken = if (currentUserId == null)
                users.existsByDiscord(discord)
            else
                users.existsByDiscordAndIdNot(discord, currentUserId)
            if (taken) {
                valid = false
                addViolation("discord", "Discord is taken.")
            }
        }

        val phoneNumber = candidate.phoneNumber
        if (!phoneNumber.isNullOrBlank()) {
            val taken = if (currentUserId == null)
                users.existsByPhoneNumber(phoneNumber)
            else
                users.existsByPhoneNumberAndIdNot(phoneNumber, currentUserId)
            if (taken) {
                valid = false
                addViolation("phoneNumber", "Phone number is taken.")
            }
        }

        return valid
    }
}

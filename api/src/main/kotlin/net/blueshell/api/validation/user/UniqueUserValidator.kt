package net.blueshell.api.validation.user

import jakarta.validation.ConstraintValidator
import jakarta.validation.ConstraintValidatorContext
import net.blueshell.api.dto.user.AdvancedUserDTO
import net.blueshell.api.dto.user.SimpleUserDTO
import net.blueshell.api.repository.UserRepository
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import org.springframework.util.StringUtils
import java.util.function.BiConsumer

@Component
class UniqueUserValidator @Autowired constructor(private val users: UserRepository) :
    ConstraintValidator<UniqueUser?, SimpleUserDTO?> {
    override fun isValid(dto: SimpleUserDTO?, context: ConstraintValidatorContext): Boolean {
        if (dto == null) return true

        var isValid = true
        val currentUserId = dto.id

        val addViolation = BiConsumer { property: String?, message: String? ->
            context.disableDefaultConstraintViolation()
            context.buildConstraintViolationWithTemplate(message)
                .addPropertyNode(property)
                .addConstraintViolation()
        }

        if (dto.username.isNotBlank()) {
            val taken = if (currentUserId == null)
                users.existsByUsername(dto.username)
            else
                users.existsByUsernameAndIdNot(dto.username, currentUserId)
            if (taken) {
                isValid = false
                addViolation.accept("username", "Username is taken.")
            }
        }

        if (!dto.email.isNullOrEmpty()) {
            val taken = if (currentUserId == null)
                users.existsByEmail(dto.email)
            else
                users.existsByEmailAndIdNot(dto.email, currentUserId)
            if (taken) {
                isValid = false
                addViolation.accept("email", "Email is taken.")
            }
        }

        if (StringUtils.hasText(dto.discord)) {
            val taken = if (currentUserId == null)
                users.existsByDiscord(dto.discord)
            else
                users.existsByDiscordAndIdNot(dto.discord, currentUserId)
            if (taken) {
                isValid = false
                addViolation.accept("discord", "Discord is taken.")
            }
        }

        if (dto is AdvancedUserDTO && StringUtils.hasText(dto.phoneNumber)) {
            val taken = if (currentUserId == null)
                users.existsByPhoneNumber(dto.phoneNumber)
            else
                users.existsByPhoneNumberAndIdNot(dto.phoneNumber, currentUserId)
            if (taken) {
                isValid = false
                addViolation.accept("phoneNumber", "Phone number is taken.")
            }
        }

        return isValid
    }
}

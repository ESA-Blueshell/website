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
        val currentUserId = dto.getId()

        val addViolation = BiConsumer { property: String?, message: String? ->
            context.disableDefaultConstraintViolation()
            context.buildConstraintViolationWithTemplate(message)
                .addPropertyNode(property)
                .addConstraintViolation()
        }

        if (StringUtils.hasText(dto.getUsername())) {
            val taken = if (currentUserId == null)
                users.existsByUsername(dto.getUsername())
            else
                users.existsByUsernameAndIdNot(dto.getUsername(), currentUserId)
            if (taken) {
                isValid = false
                addViolation.accept("username", "Username is taken.")
            }
        }

        if (StringUtils.hasText(dto.getEmail())) {
            val taken = if (currentUserId == null)
                users.existsByEmail(dto.getEmail())
            else
                users.existsByEmailAndIdNot(dto.getEmail(), currentUserId)
            if (taken) {
                isValid = false
                addViolation.accept("email", "Email is taken.")
            }
        }

        if (StringUtils.hasText(dto.getDiscord())) {
            val taken = if (currentUserId == null)
                users.existsByDiscord(dto.getDiscord())
            else
                users.existsByDiscordAndIdNot(dto.getDiscord(), currentUserId)
            if (taken) {
                isValid = false
                addViolation.accept("discord", "Discord is taken.")
            }
        }

        if (dto is AdvancedUserDTO && StringUtils.hasText(dto.getPhoneNumber())) {
            val taken = if (currentUserId == null)
                users.existsByPhoneNumber(dto.getPhoneNumber())
            else
                users.existsByPhoneNumberAndIdNot(dto.getPhoneNumber(), currentUserId)
            if (taken) {
                isValid = false
                addViolation.accept("phoneNumber", "Phone number is taken.")
            }
        }

        return isValid
    }
}

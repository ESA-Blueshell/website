package net.blueshell.api.validation.user

import jakarta.validation.ConstraintValidator
import jakarta.validation.ConstraintValidatorContext
import net.blueshell.api.dto.user.AdvancedUserDTO
import net.blueshell.api.dto.user.SimpleUserDTO
import net.blueshell.api.repository.UserRepository
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
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

        val username = dto.username
        if (!username.isNullOrBlank()) {
            val taken = if (currentUserId == null)
                users.existsByUsername(username)
            else
                users.existsByUsernameAndIdNot(username, currentUserId)
            if (taken) {
                isValid = false
                addViolation.accept("username", "Username is taken.")
            }
        }

        val email = dto.email
        if (!email.isNullOrEmpty()) {
            val taken = if (currentUserId == null)
                users.existsByEmail(email)
            else
                users.existsByEmailAndIdNot(email, currentUserId)
            if (taken) {
                isValid = false
                addViolation.accept("email", "Email is taken.")
            }
        }

        val discord = dto.discord
        if (!discord.isNullOrEmpty()) {
            val taken = if (currentUserId == null)
                users.existsByDiscord(discord)
            else
                users.existsByDiscordAndIdNot(discord, currentUserId)
            if (taken) {
                isValid = false
                addViolation.accept("discord", "Discord is taken.")
            }
        }

        if (dto is AdvancedUserDTO) {
            val phoneNumber = dto.phoneNumber
            if (!phoneNumber.isNullOrEmpty()) {
                val taken = if (currentUserId == null)
                    users.existsByPhoneNumber(phoneNumber)
                else
                    users.existsByPhoneNumberAndIdNot(phoneNumber, currentUserId)
                if (taken) {
                    isValid = false
                    addViolation.accept("phoneNumber", "Phone number is taken.")
                }
            }
        }

        return isValid
    }
}

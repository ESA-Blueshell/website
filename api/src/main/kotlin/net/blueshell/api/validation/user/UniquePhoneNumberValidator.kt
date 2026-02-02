package net.blueshell.api.validation.user

import jakarta.validation.ConstraintValidator
import jakarta.validation.ConstraintValidatorContext
import net.blueshell.api.service.UserService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import org.springframework.util.StringUtils

/**
 * Validator to check if the phone number is unique.
 */
@Component
class UniquePhoneNumberValidator @Autowired constructor(private val userService: UserService) :
    ConstraintValidator<UniquePhoneNumber?, String?> {
    override fun isValid(phoneNumber: String?, context: ConstraintValidatorContext?): Boolean {
        if (!StringUtils.hasText(phoneNumber)) {
            // Let @NotBlank or @Pattern handle this
            return true
        }
        return !userService.existsByPhoneNumber(phoneNumber)
    }
}

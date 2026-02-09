package net.blueshell.api.user.web.validation

import com.google.i18n.phonenumbers.NumberParseException
import com.google.i18n.phonenumbers.PhoneNumberUtil
import jakarta.validation.ConstraintValidator
import jakarta.validation.ConstraintValidatorContext
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

/**
 * Validator to check if the phone number is a valid mobile number using libphonenumber.
 */
@Component
class ValidMobilePhoneNumberValidator @Autowired constructor(private val phoneNumberUtil: PhoneNumberUtil) :
    ConstraintValidator<ValidMobilePhoneNumber?, String?> {
    override fun isValid(phoneNumber: String?, context: ConstraintValidatorContext?): Boolean {
        if (phoneNumber.isNullOrBlank()) {
            // Let @NotBlank or other annotations handle null/empty cases
            return true
        }

        try {
            val parsedNumber = phoneNumberUtil.parse(phoneNumber, null)
            val isValid = phoneNumberUtil.isValidNumber(parsedNumber)
            val isMobile = phoneNumberUtil.getNumberType(parsedNumber) == PhoneNumberUtil.PhoneNumberType.MOBILE
                    || phoneNumberUtil.getNumberType(parsedNumber) == PhoneNumberUtil.PhoneNumberType.FIXED_LINE_OR_MOBILE

            return isValid && isMobile
        } catch (e: NumberParseException) {
            return false
        }
    }
}

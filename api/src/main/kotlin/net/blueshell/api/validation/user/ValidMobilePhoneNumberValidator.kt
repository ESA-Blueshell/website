package net.blueshell.api.validation.user;

import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.Phonenumber;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Validator to check if the phone number is a valid mobile number using libphonenumber.
 */
@Component
public class ValidMobilePhoneNumberValidator implements ConstraintValidator<ValidMobilePhoneNumber, String> {

    private final PhoneNumberUtil phoneNumberUtil;

    @Autowired
    public ValidMobilePhoneNumberValidator(PhoneNumberUtil phoneNumberUtil) {
        this.phoneNumberUtil = phoneNumberUtil;
    }

    @Override
    public boolean isValid(String phoneNumber, ConstraintValidatorContext context) {
        if (!StringUtils.hasText(phoneNumber)) {
            // Let @NotBlank or other annotations handle null/empty cases
            return true;
        }

        try {
            Phonenumber.PhoneNumber parsedNumber = phoneNumberUtil.parse(phoneNumber, null);
            boolean isValid = phoneNumberUtil.isValidNumber(parsedNumber);
            boolean isMobile = phoneNumberUtil.getNumberType(parsedNumber) == PhoneNumberUtil.PhoneNumberType.MOBILE
                    || phoneNumberUtil.getNumberType(parsedNumber) == PhoneNumberUtil.PhoneNumberType.FIXED_LINE_OR_MOBILE;

            return isValid && isMobile;
        } catch (NumberParseException e) {
            return false;
        }
    }
}

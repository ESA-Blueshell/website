package net.blueshell.api.feature.user.validation

import jakarta.validation.ConstraintValidator
import jakarta.validation.ConstraintValidatorContext
import java.util.*

class CountryCodeValidator : ConstraintValidator<ValidCountryCode, String> {
    override fun isValid(value: String?, context: ConstraintValidatorContext?): Boolean {
        if (value.isNullOrEmpty()) {
            return true // Let @NotNull handle null/empty validation
        }

        // Check if the value is a valid ISO 3166-1 alpha-2 country code
        val isoCountries = Locale.getISOCountries()
        for (country in isoCountries) {
            if (country == value) {
                return true
            }
        }

        return false
    }
}

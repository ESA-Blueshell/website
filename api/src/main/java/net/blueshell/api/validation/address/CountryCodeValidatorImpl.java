package net.blueshell.api.validation.address;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.Locale;

public class CountryCodeValidatorImpl implements ConstraintValidator<ValidCountryCode, String> {

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null || value.isEmpty()) {
            return true; // Let @NotNull handle null/empty validation
        }

        // Check if the value is a valid ISO 3166-1 alpha-2 country code
        String[] isoCountries = Locale.getISOCountries();
        for (String country : isoCountries) {
            if (country.equals(value)) {
                return true;
            }
        }

        return false;
    }
}

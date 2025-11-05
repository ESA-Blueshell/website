package net.blueshell.api.validation;

import jakarta.validation.ConstraintValidatorContext;
import net.blueshell.api.validation.address.CountryCodeValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * Unit tests for CountryCodeValidator (ISO 3166-1 alpha-2).
 */
@SpringBootTest
class CountryCodeValidatorTest {

    private CountryCodeValidator validator;
    private ConstraintValidatorContext context;

    @BeforeEach
    void setUp() {
        validator = new CountryCodeValidator();
        context = mock(ConstraintValidatorContext.class);
    }

    @Test
    void validCountryCode() {
        assertTrue(validator.isValid("US", context));
        assertTrue(validator.isValid("NL", context));
        assertTrue(validator.isValid("DE", context));
    }

    @Test
    void invalidCountryCode() {
        assertFalse(validator.isValid("XX", context));
        assertFalse(validator.isValid("NLD", context));
        assertFalse(validator.isValid("N", context));
    }

    @Test
    void nullIsValid() {
        assertTrue(validator.isValid(null, context));
    }

    @Test
    void emptyStringIsValid() {
        assertTrue(validator.isValid("", context));
    }

    @Test
    void caseSensitiveCountryCodes() {
        assertFalse(validator.isValid("us", context));
        assertFalse(validator.isValid("nL", context));
    }
}

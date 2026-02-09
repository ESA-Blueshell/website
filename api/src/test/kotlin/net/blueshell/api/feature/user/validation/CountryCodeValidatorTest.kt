package net.blueshell.api.feature.user.validation

import jakarta.validation.ConstraintValidatorContext
import net.blueshell.api.feature.user.validation.CountryCodeValidator
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.springframework.boot.test.context.SpringBootTest

/**
 * Unit tests for CountryCodeValidator (ISO 3166-1 alpha-2).
 */
@SpringBootTest
class CountryCodeValidatorTest {

    private lateinit var validator: CountryCodeValidator
    private lateinit var context: ConstraintValidatorContext

    @BeforeEach
    fun setUp() {
        validator = CountryCodeValidator()
        context = mock<ConstraintValidatorContext>()
    }

    @Test
    fun `valid country code`() {
        assertTrue(validator.isValid("US", context))
        assertTrue(validator.isValid("NL", context))
        assertTrue(validator.isValid("DE", context))
    }

    @Test
    fun `invalid country code`() {
        assertFalse(validator.isValid("XX", context))
        assertFalse(validator.isValid("NLD", context))
        assertFalse(validator.isValid("N", context))
    }

    @Test
    fun `null is valid`() {
        assertTrue(validator.isValid(null, context))
    }

    @Test
    fun `empty string is valid`() {
        assertTrue(validator.isValid("", context))
    }

    @Test
    fun `case sensitive country codes`() {
        assertFalse(validator.isValid("us", context))
        assertFalse(validator.isValid("nL", context))
    }
}

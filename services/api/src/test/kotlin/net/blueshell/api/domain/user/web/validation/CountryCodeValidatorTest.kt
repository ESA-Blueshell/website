package net.blueshell.api.domain.user.web.validation

import jakarta.validation.ConstraintValidatorContext
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock

class CountryCodeValidatorTest {

    private val validator = CountryCodeValidator()
    private val context = mock<ConstraintValidatorContext>()

    @Test
    fun `accepts valid ISO alpha-2 codes`() {
        assertThat(validator.isValid("US", context)).isTrue()
        assertThat(validator.isValid("NL", context)).isTrue()
        assertThat(validator.isValid("DE", context)).isTrue()
    }

    @Test
    fun `rejects invalid codes and wrong casing`() {
        assertThat(validator.isValid("XX", context)).isFalse()
        assertThat(validator.isValid("NLD", context)).isFalse()
        assertThat(validator.isValid("us", context)).isFalse()
    }

    @Test
    fun `accepts null and empty`() {
        assertThat(validator.isValid(null, context)).isTrue()
        assertThat(validator.isValid("", context)).isTrue()
    }
}

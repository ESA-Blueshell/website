package net.blueshell.api.shared.validation.date

import jakarta.validation.ConstraintValidatorContext
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import java.time.LocalDate

class TodayValidatorTest {

    private val validator = TodayValidator()
    private val context = mock<ConstraintValidatorContext>()

    @Test
    fun `accepts today`() {
        assertThat(validator.isValid(LocalDate.now(), context)).isTrue()
    }

    @Test
    fun `rejects yesterday`() {
        assertThat(validator.isValid(LocalDate.now().minusDays(1), context)).isFalse()
    }

    @Test
    fun `rejects tomorrow`() {
        assertThat(validator.isValid(LocalDate.now().plusDays(1), context)).isFalse()
    }

    @Test
    fun `accepts null`() {
        assertThat(validator.isValid(null, context)).isTrue()
    }
}

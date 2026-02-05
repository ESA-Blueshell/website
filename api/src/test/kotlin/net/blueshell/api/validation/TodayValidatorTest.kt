package net.blueshell.api.validation

import jakarta.validation.ConstraintValidatorContext
import net.blueshell.api.validation.date.TodayValidator
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.springframework.boot.test.context.SpringBootTest
import java.time.LocalDate

@SpringBootTest
class TodayValidatorTest {

    private lateinit var validator: TodayValidator
    private lateinit var context: ConstraintValidatorContext

    @BeforeEach
    fun setUp() {
        validator = TodayValidator()
        context = mock()
    }

    @Test
    fun `today is valid`() {
        assertTrue(validator.isValid(LocalDate.now(), context))
    }

    @Test
    fun `yesterday is invalid`() {
        assertFalse(validator.isValid(LocalDate.now().minusDays(1), context))
    }

    @Test
    fun `tomorrow is invalid`() {
        assertFalse(validator.isValid(LocalDate.now().plusDays(1), context))
    }

    @Test
    fun `null is valid`() {
        assertTrue(validator.isValid(null, context))
    }
}

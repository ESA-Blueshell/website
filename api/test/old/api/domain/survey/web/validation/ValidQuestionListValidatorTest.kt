package net.blueshell.api.domain.survey.web.validation

import jakarta.validation.ConstraintValidatorContext
import net.blueshell.api.domain.survey.web.dto.QuestionDTO
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.mockito.kotlin.mock
import org.springframework.boot.test.context.SpringBootTest

/**
 * Unit tests for ValidQuestionListValidator (unique, non-null indices).
 */
@SpringBootTest
class ValidQuestionListValidatorTest {

    private lateinit var validator: ValidQuestionListValidator
    private lateinit var context: ConstraintValidatorContext

    @BeforeEach
    fun setUp() {
        validator = ValidQuestionListValidator()
        context = mock<ConstraintValidatorContext>(defaultAnswer = Mockito.RETURNS_DEEP_STUBS)
    }

    @Test
    fun `valid question list with unique indices`() {
        val q1 = QuestionDTO()
        q1.idx = 1L

        val q2 = QuestionDTO()
        q2.idx = 2L

        val questions = mutableListOf(q1, q2)
        assertTrue(validator.isValid(questions, context))
    }

    @Test
    fun `question list with duplicate indices is invalid`() {
        val q1 = QuestionDTO()
        q1.idx = 1L

        val q2 = QuestionDTO()
        q2.idx = 1L

        val questions = mutableListOf(q1, q2)
        assertFalse(validator.isValid(questions, context))
    }

    @Test
    fun `null question list is valid`() {
        assertTrue(validator.isValid(null, context))
    }

    @Test
    fun `empty question list is valid`() {
        assertTrue(validator.isValid(mutableListOf<QuestionDTO>(), context))
    }
}

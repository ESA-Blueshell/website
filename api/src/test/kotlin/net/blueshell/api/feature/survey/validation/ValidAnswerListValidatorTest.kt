package net.blueshell.api.feature.survey.validation

import jakarta.validation.ConstraintValidatorContext
import net.blueshell.api.feature.survey.dto.AnswerDTO
import net.blueshell.api.feature.survey.service.SurveyService
import net.blueshell.api.feature.survey.validation.ValidAnswerListValidator
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.boot.test.context.SpringBootTest
import kotlin.collections.emptyList

/**
 * Unit tests for ValidAnswerListValidator (duplicate question IDs, presence).
 */
@SpringBootTest
class ValidAnswerListValidatorTest {

    private lateinit var validator: ValidAnswerListValidator
    private lateinit var context: ConstraintValidatorContext
    private lateinit var surveyService: SurveyService

    @BeforeEach
    fun setUp() {
        surveyService = mock()
        validator = ValidAnswerListValidator()
        context = mock()
    }

    @Test
    fun `valid answer list with unique question ids`() {
        val a1 = AnswerDTO()
        a1.questionId = 1L
        val a2 = AnswerDTO()
        a2.questionId = 2L

        val answers = mutableListOf(a1, a2)
        assertTrue(validator.isValid(answers, context))
    }

    @Test
    fun `answer list with duplicate question ids is invalid`() {
        val a1 = AnswerDTO()
        a1.questionId = 1L
        val a2 = AnswerDTO()
        a2.questionId = 1L

        val answers = mutableListOf(a1, a2)

        val builder = mock<ConstraintValidatorContext.ConstraintViolationBuilder>()
        whenever(context.buildConstraintViolationWithTemplate(any<String>())).thenReturn(builder)
        whenever(builder.addConstraintViolation()).thenReturn(context)

        assertFalse(validator.isValid(answers, context))
    }

    @Test
    fun `answer with null question id is invalid`() {
        val a1 = AnswerDTO()
        a1.questionId = 1L
        val a2 = AnswerDTO()
        a2.questionId = null

        val answers = mutableListOf(a1, a2)
        assertFalse(validator.isValid(answers, context))
    }

    @Test
    fun `null answer list is valid`() {
        assertTrue(validator.isValid(null, context))
    }

    @Test
    fun `empty answer list is valid`() {
        assertTrue(validator.isValid(mutableListOf<AnswerDTO>(), context))
    }
}

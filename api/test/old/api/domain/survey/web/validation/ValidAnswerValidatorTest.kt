package net.blueshell.api.domain.survey.web.validation

import jakarta.validation.ConstraintValidatorContext
import net.blueshell.api.domain.survey.persistence.Question
import net.blueshell.api.domain.survey.persistence.repository.QuestionRepository
import net.blueshell.api.domain.survey.web.dto.AnswerDTO
import net.blueshell.api.shared.enums.QuestionType
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.boot.test.context.SpringBootTest
import java.util.*

/**
 * Unit tests for ValidAnswerValidator (per-question validation rules).
 */
@SpringBootTest
class ValidAnswerValidatorTest {

    private lateinit var questions: QuestionRepository
    private lateinit var validator: ValidAnswerValidator

    @BeforeEach
    fun setup() {
        questions = mock()
        validator = ValidAnswerValidator(questions)
    }

    @Test
    fun `invalid when question not found`() {
        val dto = AnswerDTO()
        dto.questionId = 123L
        whenever(questions.findById(123L)).thenReturn(Optional.empty())

        assertFalse(validator.isValid(dto, mock()))
    }

    @Test
    fun `open valid when non-empty text`() {
        val qid = 1L
        val question = mock<Question>()
        whenever(question.type).thenReturn(QuestionType.OPEN)
        whenever(questions.findById(qid)).thenReturn(Optional.of(question))

        val dto = AnswerDTO()
        dto.questionId = qid
        dto.textResponse = "hello"

        assertTrue(validator.isValid(dto, mock()))
    }

    @Test
    fun `open invalid when empty text`() {
        val qid = 2L
        val question = mock<Question>()
        whenever(question.type).thenReturn(QuestionType.OPEN)
        whenever(questions.findById(qid)).thenReturn(Optional.of(question))

        val dto = AnswerDTO()
        dto.questionId = qid
        dto.textResponse = ""

        assertFalse(validator.isValid(dto, mock()))
    }

    @Test
    fun `checkbox valid when size matches choices`() {
        val qid = 3L
        val question = mock<Question>()
        whenever(question.type).thenReturn(QuestionType.CHECKBOX)
        whenever(question.choiceLabels).thenReturn(mutableListOf("A", "B", "C"))
        whenever(questions.findById(qid)).thenReturn(Optional.of(question))

        val dto = AnswerDTO()
        dto.questionId = qid
        dto.optionSelections = mutableListOf(true, false, true)

        assertTrue(validator.isValid(dto, mock()))
    }

    @Test
    fun `checkbox invalid when size mismatch`() {
        val qid = 4L
        val question = mock<Question>()
        whenever(question.type).thenReturn(QuestionType.CHECKBOX)
        whenever(question.choiceLabels).thenReturn(mutableListOf("A", "B", "C"))
        whenever(questions.findById(qid)).thenReturn(Optional.of(question))

        val dto = AnswerDTO()
        dto.questionId = qid
        dto.optionSelections = mutableListOf(true, false)

        assertFalse(validator.isValid(dto, mock()))
    }

    @Test
    fun `radio valid when exactly one true and size matches`() {
        val qid = 5L
        val question = mock<Question>()
        whenever(question.type).thenReturn(QuestionType.RADIO)
        whenever(question.choiceLabels).thenReturn(mutableListOf("Red", "Blue"))
        whenever(questions.findById(qid)).thenReturn(Optional.of(question))

        val dto = AnswerDTO()
        dto.questionId = qid
        dto.optionSelections = mutableListOf(true, false)

        assertTrue(validator.isValid(dto, mock()))
    }

    @Test
    fun `radio invalid when zero or multiple selected`() {
        val qid = 6L
        val question = mock<Question>()
        whenever(question.type).thenReturn(QuestionType.RADIO)
        whenever(question.choiceLabels).thenReturn(mutableListOf("Red", "Blue"))
        whenever(questions.findById(qid)).thenReturn(Optional.of(question))

        val ctx = mock<ConstraintValidatorContext>()

        val none = AnswerDTO()
        none.questionId = qid
        none.optionSelections = mutableListOf(false, false)
        assertFalse(validator.isValid(none, ctx))

        val many = AnswerDTO()
        many.questionId = qid
        many.optionSelections = mutableListOf(true, true)
        assertFalse(validator.isValid(many, ctx))

        val mismatch = AnswerDTO()
        mismatch.questionId = qid
        mismatch.optionSelections = mutableListOf(true, false, false)
        assertFalse(validator.isValid(mismatch, ctx))
    }
}

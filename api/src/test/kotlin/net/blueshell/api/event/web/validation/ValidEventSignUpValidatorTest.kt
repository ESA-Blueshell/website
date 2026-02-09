package net.blueshell.api.event.web.validation

import jakarta.validation.ConstraintViolation
import jakarta.validation.Validator
import net.blueshell.api.shared.enums.QuestionType
import net.blueshell.api.event.web.dto.EventSignUpDTO
import net.blueshell.api.survey.web.dto.AnswerDTO
import net.blueshell.api.event.persistence.Event
import net.blueshell.api.survey.persistence.Question
import net.blueshell.api.survey.persistence.Survey
import net.blueshell.api.survey.persistence.QuestionRepository
import net.blueshell.api.event.application.EventService
import net.blueshell.api.testutil.ModelTestUtils
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.bean.override.mockito.MockitoBean
import java.util.Optional

/**
 * Unit tests for composite EventSignUpDTO validator using mocked survey metadata.
 */
@SpringBootTest
class ValidEventSignUpValidatorTest @Autowired constructor(
    private val validator: Validator
) {

    @MockitoBean
    private lateinit var questionRepository: QuestionRepository

    @MockitoBean
    private lateinit var eventService: EventService

    @BeforeEach
    fun setupQuestions() {
        val openQuestion = mkQuestion(Q_OPEN, QuestionType.OPEN, null)
        val radioQuestion = mkQuestion(Q_RADIO, QuestionType.RADIO, mutableListOf("A", "B", "C"))
        val checkboxQuestion = mkQuestion(Q_CHECK, QuestionType.CHECKBOX, mutableListOf("X", "Y", "Z", "W"))

        whenever(questionRepository.findById(Q_OPEN)).thenReturn(Optional.of(openQuestion))
        whenever(questionRepository.findById(Q_RADIO)).thenReturn(Optional.of(radioQuestion))
        whenever(questionRepository.findById(Q_CHECK)).thenReturn(Optional.of(checkboxQuestion))

        val form = Survey()
        form.questions.addAll(setOf(openQuestion, radioQuestion, checkboxQuestion))

        val event = Event()
        ModelTestUtils.setId(event, 1L)
        event.signUpForm = form

        whenever(eventService.findById(1L)).thenReturn(event)
    }

    @Test
    fun `valid payload with all question types passes validation`() {
        val answers = mutableListOf<AnswerDTO>()
        answers.add(openAnswer(Q_OPEN, "I love this event"))
        answers.add(radioAnswer(mutableListOf(true, false, false)))
        answers.add(checkboxAnswer(mutableListOf(true, false, true, false)))

        val dto = dtoWithAnswers(answers)
        val violations: Set<ConstraintViolation<EventSignUpDTO>> = validator.validate(dto)
        assertTrue(violations.isEmpty(), "Expected a valid EventSignUpDTO")
    }

    @Test
    fun `radio with zero selected is invalid`() {
        val answers = mutableListOf<AnswerDTO>()
        answers.add(openAnswer(Q_OPEN, "ok"))
        answers.add(radioAnswer(mutableListOf(false, false, false)))
        answers.add(checkboxAnswer(mutableListOf(true, false, false, false)))

        val dto = dtoWithAnswers(answers)
        val violations: Set<ConstraintViolation<EventSignUpDTO>> = validator.validate(dto)
        assertFalse(violations.isEmpty())
        assertTrue(violations.any { it.propertyPath.toString().contains("answers") })
    }

    @Test
    fun `radio with multiple selected is invalid`() {
        val answers = mutableListOf<AnswerDTO>()
        answers.add(openAnswer(Q_OPEN, "ok"))
        answers.add(radioAnswer(mutableListOf(true, true, false)))
        answers.add(checkboxAnswer(mutableListOf(true, false, true, false)))

        val dto = dtoWithAnswers(answers)
        val violations: Set<ConstraintViolation<EventSignUpDTO>> = validator.validate(dto)
        assertFalse(violations.isEmpty())
        assertTrue(violations.any { it.propertyPath.toString().contains("answers") })
    }

    @Test
    fun `checkbox with length mismatch is invalid`() {
        val answers = mutableListOf<AnswerDTO>()
        answers.add(openAnswer(Q_OPEN, "ok"))
        answers.add(radioAnswer(mutableListOf(false, true, false)))
        answers.add(checkboxAnswer(mutableListOf(true, false, true)))

        val dto = dtoWithAnswers(answers)
        val violations: Set<ConstraintViolation<EventSignUpDTO>> = validator.validate(dto)
        assertFalse(violations.isEmpty())
        assertTrue(violations.any { it.propertyPath.toString().contains("answers") })
    }

    @Test
    fun `open without text is invalid`() {
        val answers = mutableListOf<AnswerDTO>()
        answers.add(openAnswer(Q_OPEN, ""))
        answers.add(radioAnswer(mutableListOf(false, true, false)))
        answers.add(checkboxAnswer(mutableListOf(true, false, true, false)))

        val dto = dtoWithAnswers(answers)
        val violations: Set<ConstraintViolation<EventSignUpDTO>> = validator.validate(dto)
        assertFalse(violations.isEmpty())
        assertTrue(violations.any { it.propertyPath.toString().contains("answers") })
    }

    @Test
    fun `duplicate answers for same question are invalid`() {
        val answers = mutableListOf<AnswerDTO>()
        answers.add(openAnswer(Q_OPEN, "text"))
        answers.add(radioAnswer(mutableListOf(true, false, false)))
        answers.add(checkboxAnswer(mutableListOf(true, false, false, false)))
        answers.add(radioAnswer(mutableListOf(false, true, false)))

        val dto = dtoWithAnswers(answers)
        val violations: Set<ConstraintViolation<EventSignUpDTO>> = validator.validate(dto)
        assertFalse(violations.isEmpty())
        assertTrue(violations.any { it.propertyPath.toString().contains("answers") })
    }

    @Test
    fun `answer referring to unknown question is invalid`() {
        val unknownId = 9999L
        whenever(questionRepository.findById(unknownId)).thenReturn(Optional.empty())

        val answers = mutableListOf<AnswerDTO>()
        answers.add(openAnswer(Q_OPEN, "text"))
        answers.add(radioAnswer(mutableListOf(true, false, false)))
        answers.add(checkboxAnswer(mutableListOf(true, false, false, false)))
        answers.add(openAnswer(unknownId, "should fail"))

        val dto = dtoWithAnswers(answers)
        val violations: Set<ConstraintViolation<EventSignUpDTO>> = validator.validate(dto)
        assertFalse(violations.isEmpty())
        assertTrue(violations.any { it.propertyPath.toString().contains("answers") })
    }

    private fun mkQuestion(id: Long, type: QuestionType, choices: MutableList<String>?): Question {
        val question = Question()
        ModelTestUtils.setId(question, id)
        question.type = type
        choices?.let { question.choiceLabels = it }
        return question
    }

    private fun dtoWithAnswers(answers: MutableList<AnswerDTO>): EventSignUpDTO {
        val dto = EventSignUpDTO()
        dto.eventId = 1L
        dto.userId = 42L
        dto.answers = answers
        return dto
    }

    private fun openAnswer(questionId: Long, text: String): AnswerDTO {
        val answer = AnswerDTO()
        answer.questionId = questionId
        answer.textResponse = text
        answer.optionSelections = null
        return answer
    }

    private fun radioAnswer(selections: MutableList<Boolean>): AnswerDTO {
        val answer = AnswerDTO()
        answer.questionId = Q_RADIO
        answer.textResponse = null
        answer.optionSelections = selections
        return answer
    }

    private fun checkboxAnswer(selections: MutableList<Boolean>): AnswerDTO {
        val answer = AnswerDTO()
        answer.questionId = Q_CHECK
        answer.textResponse = null
        answer.optionSelections = selections
        return answer
    }

    companion object {
        private const val Q_OPEN = 101L
        private const val Q_RADIO = 102L
        private const val Q_CHECK = 103L
    }
}

package net.blueshell.api.survey.api.validation

import jakarta.validation.ConstraintViolation
import jakarta.validation.Validator
import net.blueshell.api.shared.enums.QuestionType
import net.blueshell.api.survey.api.dto.AnswerDTO
import net.blueshell.api.survey.api.dto.QuestionDTO
import net.blueshell.api.survey.api.dto.SurveyDTO
import net.blueshell.api.factory.dto.survey.AnswerDTOFactory
import net.blueshell.api.factory.dto.survey.QuestionDTOFactory
import net.blueshell.api.factory.dto.survey.SurveyDTOFactory
import net.blueshell.api.survey.domain.model.Question
import net.blueshell.api.survey.persistence.QuestionRepository
import net.blueshell.api.testutil.ModelTestUtils
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.bean.override.mockito.MockitoBean
import java.util.Optional

/**
 * Unit tests for Survey, Question, and Answer DTO validations.
 */
@SpringBootTest
class SurveyValidatorTest @Autowired constructor(
    private val validator: Validator,
    private val surveyFactory: SurveyDTOFactory,
    private val questionFactory: QuestionDTOFactory,
    private val answerFactory: AnswerDTOFactory
) {

    @MockitoBean
    private lateinit var questionRepository: QuestionRepository

    private fun mkQuestion(type: QuestionType): Question {
        val question = Question()
        ModelTestUtils.setId(question, 42L)
        question.type = type
        if (type == QuestionType.RADIO || type == QuestionType.CHECKBOX) {
            question.choiceLabels = mutableListOf("A", "B", "C")
        }
        return question
    }

    @Test
    fun `valid survey dto passes validation`() {
        val dto = surveyFactory.createBasic()
        val violations: Set<ConstraintViolation<SurveyDTO>> = validator.validate(dto)
        assertTrue(violations.isEmpty(), "Valid SurveyDTO should pass validation")
    }

    @Test
    fun `survey dto with empty questions fails validation`() {
        val dto = surveyFactory.createWithCustomizations { it.questions = mutableListOf() }
        val violations: Set<ConstraintViolation<SurveyDTO>> = validator.validate(dto)
        assertFalse(violations.isEmpty())
        assertTrue(violations.any { it.propertyPath.toString() == "questions" })
    }

    @Test
    fun `valid question dto passes validation`() {
        val dto = questionFactory.createBasic()
        val violations: Set<ConstraintViolation<QuestionDTO>> = validator.validate(dto)
        assertTrue(violations.isEmpty(), "Valid QuestionDTO should pass validation")
    }

    @Test
    fun `question dto without label fails validation`() {
        val dto = questionFactory.createWithCustomizations { it.label = "" }
        val violations: Set<ConstraintViolation<QuestionDTO>> = validator.validate(dto)
        assertFalse(violations.isEmpty())
        assertTrue(violations.any { it.propertyPath.toString() == "label" })
    }

    @Test
    fun `question dto with long label fails validation`() {
        val longLabel = "A".repeat(2056)
        val dto = questionFactory.createWithCustomizations { it.label = longLabel }
        val violations: Set<ConstraintViolation<QuestionDTO>> = validator.validate(dto)
        assertFalse(violations.isEmpty())
        assertTrue(violations.any { it.propertyPath.toString() == "label" })
    }

    @Test
    fun `open question with choice labels fails validation`() {
        val dto = questionFactory.createWithCustomizations {
            it.type = QuestionType.OPEN
            it.choiceLabels = mutableListOf("Choice 1", "Choice 2")
        }
        val violations: Set<ConstraintViolation<QuestionDTO>> = validator.validate(dto)
        assertFalse(violations.isEmpty())
    }

    @Test
    fun `checkbox question without choice labels fails validation`() {
        val dto = questionFactory.createWithCustomizations {
            it.type = QuestionType.CHECKBOX
            it.choiceLabels = mutableListOf()
        }
        val violations: Set<ConstraintViolation<QuestionDTO>> = validator.validate(dto)
        assertFalse(violations.isEmpty())
    }

    @Test
    fun `valid answer dto passes validation`() {
        val dto = answerFactory.createBasic()
        whenever(questionRepository.findById(dto.questionId)).thenReturn(Optional.of(mkQuestion(QuestionType.OPEN)))

        val violations: Set<ConstraintViolation<AnswerDTO>> = validator.validate(dto)
        assertTrue(violations.isEmpty(), "Valid AnswerDTO should pass validation")
    }

    @Test
    fun `answer dto without question id fails validation`() {
        val dto = answerFactory.createWithCustomizations { it.questionId = null }
        val violations: Set<ConstraintViolation<AnswerDTO>> = validator.validate(dto)
        assertFalse(violations.isEmpty())
        assertTrue(violations.any { it.propertyPath.toString() == "questionId" })
    }
}

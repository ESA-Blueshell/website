package net.blueshell.api.survey.validation

import jakarta.validation.ConstraintValidatorContext
import net.blueshell.api.shared.enums.QuestionType
import net.blueshell.api.survey.dto.QuestionDTO
import net.blueshell.api.survey.validation.ValidQuestionValidator
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.springframework.boot.test.context.SpringBootTest
import java.util.Collections.emptyList

/**
 * Unit tests for ValidQuestionValidator.
 */
@SpringBootTest
class ValidQuestionValidatorTest {

    private lateinit var validator: ValidQuestionValidator
    private lateinit var context: ConstraintValidatorContext

    @BeforeEach
    fun setUp() {
        validator = ValidQuestionValidator()
        context = mock<ConstraintValidatorContext>()
    }

    @Test
    fun `open question without choices is valid`() {
        val dto = QuestionDTO()
        dto.type = QuestionType.OPEN
        dto.choiceLabels = null
        assertTrue(validator.isValid(dto, context))
    }

    @Test
    fun `open question with empty choices is valid`() {
        val dto = QuestionDTO()
        dto.type = QuestionType.OPEN
        dto.choiceLabels = emptyList()
        assertTrue(validator.isValid(dto, context))
    }

    @Test
    fun `checkbox question with valid choices is valid`() {
        val dto = QuestionDTO()
        dto.type = QuestionType.CHECKBOX
        dto.choiceLabels = mutableListOf("Option 1", "Option 2", "Option 3")
        assertTrue(validator.isValid(dto, context))
    }

    @Test
    fun `checkbox question without choices is invalid`() {
        val dto = QuestionDTO()
        dto.type = QuestionType.CHECKBOX
        dto.choiceLabels = null
        assertFalse(validator.isValid(dto, context))
    }

    @Test
    fun `checkbox question with empty choices is invalid`() {
        val dto = QuestionDTO()
        dto.type = QuestionType.CHECKBOX
        dto.choiceLabels = emptyList()
        assertFalse(validator.isValid(dto, context))
    }

    @Test
    fun `checkbox question with empty choice label is invalid`() {
        val dto = QuestionDTO()
        dto.type = QuestionType.CHECKBOX
        dto.choiceLabels = mutableListOf("Option 1", "", "Option 3")
        assertFalse(validator.isValid(dto, context))
    }

    @Test
    fun `radio question with valid choices is valid`() {
        val dto = QuestionDTO()
        dto.type = QuestionType.RADIO
        dto.choiceLabels = mutableListOf("Yes", "No")
        assertTrue(validator.isValid(dto, context))
    }

    @Test
    fun `null question is valid`() {
        assertTrue(validator.isValid(null, context))
    }

    @Test
    fun `null question type is valid`() {
        val dto = QuestionDTO()
        dto.type = null
        assertTrue(validator.isValid(dto, context))
    }
}

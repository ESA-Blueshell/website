package net.blueshell.api.domain.survey.web.validation

import jakarta.validation.ConstraintValidatorContext
import net.blueshell.api.domain.survey.web.dto.QuestionDTO
import net.blueshell.api.shared.enums.QuestionType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock

class ValidQuestionValidatorTest {

    private val validator = ValidQuestionValidator()
    private val context = mock<ConstraintValidatorContext>()

    @Test
    fun `accepts open and description without choices`() {
        assertThat(validator.isValid(question(QuestionType.OPEN, null), context)).isTrue()
        assertThat(validator.isValid(question(QuestionType.DESCRIPTION, mutableListOf()), context)).isTrue()
    }

    @Test
    fun `rejects open and description with choice labels`() {
        assertThat(validator.isValid(question(QuestionType.OPEN, mutableListOf("A")), context)).isFalse()
        assertThat(validator.isValid(question(QuestionType.DESCRIPTION, mutableListOf("A")), context)).isFalse()
    }

    @Test
    fun `accepts checkbox and radio with non-blank choices`() {
        assertThat(validator.isValid(question(QuestionType.CHECKBOX, mutableListOf("A", "B")), context)).isTrue()
        assertThat(validator.isValid(question(QuestionType.RADIO, mutableListOf("Yes", "No")), context)).isTrue()
    }

    @Test
    fun `rejects checkbox and radio without valid choices`() {
        assertThat(validator.isValid(question(QuestionType.CHECKBOX, null), context)).isFalse()
        assertThat(validator.isValid(question(QuestionType.RADIO, mutableListOf("A", "  ")), context)).isFalse()
    }

    @Test
    fun `accepts null question`() {
        assertThat(validator.isValid(null, context)).isTrue()
    }

    private fun question(type: QuestionType?, choices: MutableList<String>?): QuestionDTO {
        return QuestionDTO(type = type, choiceLabels = choices)
    }
}

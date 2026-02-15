package net.blueshell.api.domain.survey.web.validation

import jakarta.validation.ConstraintValidatorContext
import net.blueshell.api.domain.survey.web.dto.QuestionDTO
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.mockito.kotlin.mock

class ValidQuestionListValidatorTest {

    private val validator = ValidQuestionListValidator()
    private val context = mock<ConstraintValidatorContext>(defaultAnswer = Mockito.RETURNS_DEEP_STUBS)

    @Test
    fun `accepts null and empty lists`() {
        assertThat(validator.isValid(null, context)).isTrue()
        assertThat(validator.isValid(mutableListOf(), context)).isTrue()
    }

    @Test
    fun `accepts unique question indices`() {
        val questions = mutableListOf(QuestionDTO(idx = 1), QuestionDTO(idx = 2))

        assertThat(validator.isValid(questions, context)).isTrue()
    }

    @Test
    fun `rejects duplicate question indices`() {
        val questions = mutableListOf(QuestionDTO(idx = 1), QuestionDTO(idx = 1))

        assertThat(validator.isValid(questions, context)).isFalse()
    }
}

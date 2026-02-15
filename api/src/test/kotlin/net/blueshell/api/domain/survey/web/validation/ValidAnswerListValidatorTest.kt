package net.blueshell.api.domain.survey.web.validation

import jakarta.validation.ConstraintValidatorContext
import net.blueshell.api.domain.survey.web.dto.AnswerDTO
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.mockito.kotlin.mock

class ValidAnswerListValidatorTest {

    private val validator = ValidAnswerListValidator()
    private val context = mock<ConstraintValidatorContext>(defaultAnswer = Mockito.RETURNS_DEEP_STUBS)

    @Test
    fun `accepts null and empty lists`() {
        assertThat(validator.isValid(null, context)).isTrue()
        assertThat(validator.isValid(mutableListOf(), context)).isTrue()
    }

    @Test
    fun `accepts unique question ids`() {
        val answers = mutableListOf(AnswerDTO(questionId = 1), AnswerDTO(questionId = 2))

        assertThat(validator.isValid(answers, context)).isTrue()
    }

    @Test
    fun `rejects duplicate question ids`() {
        val answers = mutableListOf(AnswerDTO(questionId = 1), AnswerDTO(questionId = 1))

        assertThat(validator.isValid(answers, context)).isFalse()
    }
}

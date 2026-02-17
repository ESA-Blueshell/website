package net.blueshell.api.domain.survey.application.validation

import jakarta.validation.ConstraintValidatorContext
import net.blueshell.api.domain.survey.command.AnswerData
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
        val answers = mutableListOf(AnswerData(questionId = 1), AnswerData(questionId = 2))

        assertThat(validator.isValid(answers, context)).isTrue()
    }

    @Test
    fun `rejects duplicate question ids`() {
        val answers = mutableListOf(AnswerData(questionId = 1), AnswerData(questionId = 1))

        assertThat(validator.isValid(answers, context)).isFalse()
    }
}

package net.blueshell.api.survey.domain

import jakarta.validation.ConstraintValidatorContext
import net.blueshell.api.survey.api.QuestionData
import net.blueshell.api.shared.enums.QuestionType
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
        val questions = mutableListOf(question(1), question(2))

        assertThat(validator.isValid(questions, context)).isTrue()
    }

    @Test
    fun `rejects duplicate question indices`() {
        val questions = mutableListOf(question(1), question(1))

        assertThat(validator.isValid(questions, context)).isFalse()
    }

    private fun question(idx: Long): QuestionData =
        QuestionData(
            idx = idx,
            type = QuestionType.OPEN,
            label = "Question $idx",
            choiceLabels = null
        )
}

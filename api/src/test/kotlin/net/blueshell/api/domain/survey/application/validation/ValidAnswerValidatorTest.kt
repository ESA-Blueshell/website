package net.blueshell.api.domain.survey.application.validation

import net.blueshell.api.domain.survey.application.QuestionService
import net.blueshell.api.domain.survey.command.AnswerData
import net.blueshell.api.domain.survey.persistence.Question
import net.blueshell.api.domain.survey.persistence.Survey
import net.blueshell.api.shared.enums.QuestionType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class ValidAnswerValidatorTest {

    private val questions = mock<QuestionService>()
    private val validator = ValidAnswerValidator(questions)

    @Test
    fun `accepts null answer`() {
        assertThat(validator.isValid(null, mock())).isTrue()
    }

    @Test
    fun `rejects when question id is missing or unknown`() {
        whenever(questions.findById(999)).thenThrow(RuntimeException("not found"))
        assertThat(validator.isValid(AnswerData(questionId = 999), mock())).isFalse()
    }

    @Test
    fun `validates open text answers`() {
        whenever(questions.findById(1)).thenReturn(question(type = QuestionType.OPEN))

        assertThat(validator.isValid(AnswerData(questionId = 1, textResponse = "hello"), mock())).isTrue()
        assertThat(validator.isValid(AnswerData(questionId = 1, textResponse = "  "), mock())).isFalse()
    }

    @Test
    fun `validates checkbox selection size against labels`() {
        whenever(questions.findById(2)).thenReturn(question(type = QuestionType.CHECKBOX, choices = mutableListOf("A", "B", "C")))

        val valid = AnswerData(questionId = 2, optionSelections = mutableListOf(true, false, true))
        val wrongSize = AnswerData(questionId = 2, optionSelections = mutableListOf(true, false))
        val missingSelections = AnswerData(questionId = 2, optionSelections = null)

        assertThat(validator.isValid(valid, mock())).isTrue()
        assertThat(validator.isValid(wrongSize, mock())).isFalse()
        assertThat(validator.isValid(missingSelections, mock())).isFalse()
    }

    @Test
    fun `validates radio selection count and size`() {
        whenever(questions.findById(3)).thenReturn(question(type = QuestionType.RADIO, choices = mutableListOf("A", "B")))

        val exactlyOne = AnswerData(questionId = 3, optionSelections = mutableListOf(true, false))
        val none = AnswerData(questionId = 3, optionSelections = mutableListOf(false, false))
        val multiple = AnswerData(questionId = 3, optionSelections = mutableListOf(true, true))
        val wrongSize = AnswerData(questionId = 3, optionSelections = mutableListOf(true, false, false))

        assertThat(validator.isValid(exactlyOne, mock())).isTrue()
        assertThat(validator.isValid(none, mock())).isFalse()
        assertThat(validator.isValid(multiple, mock())).isFalse()
        assertThat(validator.isValid(wrongSize, mock())).isFalse()
    }

    @Test
    fun `description question always passes`() {
        whenever(questions.findById(any())).thenReturn(question(type = QuestionType.DESCRIPTION))

        val dto = AnswerData(questionId = 4, optionSelections = null, textResponse = null)
        assertThat(validator.isValid(dto, mock())).isTrue()
    }

    private fun question(type: QuestionType, choices: MutableList<String>? = null): Question {
        return Question(
            idx = 0L,
            survey = Survey(),
            type = type,
            label = "Q",
            choiceLabels = choices,
        )
    }
}

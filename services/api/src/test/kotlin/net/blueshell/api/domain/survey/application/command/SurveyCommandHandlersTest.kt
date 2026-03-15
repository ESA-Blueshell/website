package net.blueshell.api.domain.survey.application.command

import net.blueshell.api.domain.survey.application.SurveyService
import net.blueshell.api.domain.survey.command.CreateSurveyCommand
import net.blueshell.api.domain.survey.command.DeleteSurveyByIdCommand
import net.blueshell.api.domain.survey.command.FindSurveyByIdCommand
import net.blueshell.api.domain.survey.command.FindSurveysCommand
import net.blueshell.api.domain.survey.command.QuestionData
import net.blueshell.api.domain.survey.command.UpdateSurveyCommand
import net.blueshell.api.domain.survey.persistence.Survey
import net.blueshell.api.shared.enums.QuestionType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class SurveyCommandHandlersTest {

    private val surveyService = mock<SurveyService>()

    @Nested
    inner class CreateSurvey {

        private val handler = CreateSurveyHandler(surveyService)

        @Test
        fun `creates survey with mapped questions`() {
            val captured = argumentCaptor<Survey>()
            whenever(surveyService.create(captured.capture())).thenAnswer { captured.firstValue }

            val result = handler.handle(CreateSurveyCommand(questions = sampleQuestions().toMutableList()))

            assertThat(captured.firstValue.questions).hasSize(2)
            assertThat(captured.firstValue.questions.map { it.idx }).containsExactly(0L, 1L)
            assertThat(captured.firstValue.questions.map { it.type }).containsExactly(QuestionType.OPEN, QuestionType.RADIO)
            assertThat(captured.firstValue.questions.map { it.label }).containsExactly("Question one", "Question two")
            assertThat(captured.firstValue.questions.map { it.choiceLabels }).containsExactly(null, mutableListOf("A", "B"))
            assertThat(captured.firstValue.questions.all { it.survey === captured.firstValue }).isTrue()
            assertThat(result).isSameAs(captured.firstValue)
        }
    }

    @Nested
    inner class UpdateSurvey {

        private val handler = UpdateSurveyHandler(surveyService)

        @Test
        fun `updates survey with replacement question set`() {
            val existing = Survey()
            whenever(surveyService.findById(5L)).thenReturn(existing)
            whenever(surveyService.update(existing)).thenReturn(existing)

            val result = handler.handle(
                UpdateSurveyCommand(
                    id = 5L,
                    questions = sampleQuestions().toMutableList()
                )
            )

            assertThat(existing.questions).hasSize(2)
            assertThat(existing.questions.map { it.idx }).containsExactly(0L, 1L)
            assertThat(existing.questions.all { it.survey === existing }).isTrue()
            assertThat(result).isSameAs(existing)
        }
    }

    @Nested
    inner class FindSurveys {

        private val handler = FindSurveysHandler(surveyService)

        @Test
        fun `returns all surveys`() {
            val expected = mutableListOf(Survey())
            whenever(surveyService.findAll()).thenReturn(expected)

            val result = handler.handle(FindSurveysCommand())

            assertThat(result).isSameAs(expected)
            verify(surveyService).findAll()
        }
    }

    @Nested
    inner class FindSurveyById {

        private val handler = FindSurveyByIdHandler(surveyService)

        @Test
        fun `returns survey by id`() {
            val expected = Survey()
            whenever(surveyService.findById(9L)).thenReturn(expected)

            val result = handler.handle(FindSurveyByIdCommand(9L))

            assertThat(result).isSameAs(expected)
            verify(surveyService).findById(9L)
        }
    }

    @Nested
    inner class DeleteSurveyById {

        private val handler = DeleteSurveyByIdHandler(surveyService)

        @Test
        fun `deletes survey by id`() {
            handler.handle(DeleteSurveyByIdCommand(11L))

            verify(surveyService).deleteById(eq(11L))
        }
    }

    private fun sampleQuestions(): List<QuestionData> = listOf(
        QuestionData(
            idx = 0L,
            type = QuestionType.OPEN,
            label = "Question one",
            choiceLabels = null
        ),
        QuestionData(
            idx = 1L,
            type = QuestionType.RADIO,
            label = "Question two",
            choiceLabels = listOf("A", "B")
        )
    )
}

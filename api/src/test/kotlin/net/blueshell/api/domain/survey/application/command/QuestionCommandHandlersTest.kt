package net.blueshell.api.domain.survey.application.command

import net.blueshell.api.domain.survey.application.QuestionService
import net.blueshell.api.domain.survey.application.SurveyService
import net.blueshell.api.domain.survey.command.CreateQuestionCommand
import net.blueshell.api.domain.survey.command.DeleteQuestionByIdCommand
import net.blueshell.api.domain.survey.command.FindQuestionByIdCommand
import net.blueshell.api.domain.survey.command.FindQuestionsCommand
import net.blueshell.api.domain.survey.command.UpdateQuestionCommand
import net.blueshell.api.domain.survey.persistence.Question
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

class QuestionCommandHandlersTest {

    private val questionService = mock<QuestionService>()
    private val surveyService = mock<SurveyService>()

    @Nested
    inner class CreateQuestion {

        private val handler = CreateQuestionHandler(questionService, surveyService)

        @Test
        fun `creates question with survey and fields`() {
            val survey = mock<Survey>()
            whenever(surveyService.findById(6L)).thenReturn(survey)
            val captured = argumentCaptor<Question>()
            whenever(questionService.create(captured.capture())).thenAnswer { captured.firstValue }

            val result = handler.handle(
                CreateQuestionCommand(
                    idx = 1L,
                    surveyId = 6L,
                    type = QuestionType.CHECKBOX,
                    label = "Pick options",
                    choiceLabels = mutableListOf("A", "B")
                )
            )

            assertThat(captured.firstValue.idx).isEqualTo(1L)
            assertThat(captured.firstValue.type).isEqualTo(QuestionType.CHECKBOX)
            assertThat(captured.firstValue.label).isEqualTo("Pick options")
            assertThat(captured.firstValue.choiceLabels).containsExactly("A", "B")
            assertThat(captured.firstValue.survey).isSameAs(survey)
            assertThat(result).isSameAs(captured.firstValue)
        }
    }

    @Nested
    inner class UpdateQuestion {

        private val handler = UpdateQuestionHandler(questionService, surveyService)

        @Test
        fun `updates question with survey and fields`() {
            val existing = Question(
                idx = 1L,
                survey = mock(),
                type = QuestionType.OPEN,
                label = "Existing",
            )
            val survey = mock<Survey>()
            whenever(questionService.findById(7L)).thenReturn(existing)
            whenever(surveyService.findById(8L)).thenReturn(survey)
            whenever(questionService.update(existing)).thenReturn(existing)

            val result = handler.handle(
                UpdateQuestionCommand(
                    id = 7L,
                    idx = 2L,
                    surveyId = 8L,
                    type = QuestionType.OPEN,
                    label = "Describe",
                    choiceLabels = null
                )
            )

            assertThat(existing.idx).isEqualTo(2L)
            assertThat(existing.type).isEqualTo(QuestionType.OPEN)
            assertThat(existing.label).isEqualTo("Describe")
            assertThat(existing.choiceLabels).isNull()
            assertThat(existing.survey).isSameAs(survey)
            assertThat(result).isSameAs(existing)
        }
    }

    @Nested
    inner class FindQuestions {

        private val handler = FindQuestionsHandler(questionService)

        @Test
        fun `returns all questions`() {
            val expected = mutableListOf(
                Question(
                    idx = 1L,
                    survey = mock(),
                    type = QuestionType.OPEN,
                    label = "Q",
                )
            )
            whenever(questionService.findAll()).thenReturn(expected)

            val result = handler.handle(FindQuestionsCommand())

            assertThat(result).isSameAs(expected)
            verify(questionService).findAll()
        }
    }

    @Nested
    inner class FindQuestionById {

        private val handler = FindQuestionByIdHandler(questionService)

        @Test
        fun `returns question by id`() {
            val expected = Question(
                idx = 1L,
                survey = mock(),
                type = QuestionType.OPEN,
                label = "Q",
            )
            whenever(questionService.findById(9L)).thenReturn(expected)

            val result = handler.handle(FindQuestionByIdCommand(9L))

            assertThat(result).isSameAs(expected)
            verify(questionService).findById(9L)
        }
    }

    @Nested
    inner class DeleteQuestionById {

        private val handler = DeleteQuestionByIdHandler(questionService)

        @Test
        fun `deletes question by id`() {
            handler.handle(DeleteQuestionByIdCommand(10L))

            verify(questionService).deleteById(eq(10L))
        }
    }
}

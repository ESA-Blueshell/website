package net.blueshell.api.domain.survey.application.command

import net.blueshell.api.domain.survey.application.AnswerService
import net.blueshell.api.domain.survey.application.QuestionService
import net.blueshell.api.domain.survey.command.CreateAnswerCommand
import net.blueshell.api.domain.survey.command.DeleteAnswerByIdCommand
import net.blueshell.api.domain.survey.command.FindAnswerByIdCommand
import net.blueshell.api.domain.survey.command.FindAnswersByQuestionIdCommand
import net.blueshell.api.domain.survey.command.FindAnswersBySurveyIdCommand
import net.blueshell.api.domain.survey.command.FindAnswersCommand
import net.blueshell.api.domain.survey.command.UpdateAnswerCommand
import net.blueshell.api.domain.survey.persistence.Answer
import net.blueshell.api.domain.survey.persistence.Question
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class AnswerCommandHandlersTest {

    private val answerService = mock<AnswerService>()
    private val questionService = mock<QuestionService>()

    @Nested
    inner class CreateAnswer {

        private val handler = CreateAnswerHandler(answerService, questionService)

        @Test
        fun `creates answer with question and response fields`() {
            val question = mock<Question>()
            whenever(questionService.findById(3L)).thenReturn(question)
            val captured = argumentCaptor<Answer>()
            whenever(answerService.create(captured.capture())).thenAnswer { captured.firstValue }

            val result = handler.handle(
                CreateAnswerCommand(
                    questionId = 3L,
                    optionSelections = mutableListOf(true, false),
                    textResponse = "My answer"
                )
            )

            assertThat(captured.firstValue.question).isSameAs(question)
            assertThat(captured.firstValue.optionSelections).containsExactly(true, false)
            assertThat(captured.firstValue.textResponse).isEqualTo("My answer")
            assertThat(result).isSameAs(captured.firstValue)
        }
    }

    @Nested
    inner class UpdateAnswer {

        private val handler = UpdateAnswerHandler(answerService, questionService)

        @Test
        fun `updates answer with question and response fields`() {
            val existing = Answer()
            val question = mock<Question>()
            whenever(answerService.findById(7L)).thenReturn(existing)
            whenever(questionService.findById(4L)).thenReturn(question)
            whenever(answerService.update(existing)).thenReturn(existing)

            val result = handler.handle(
                UpdateAnswerCommand(
                    id = 7L,
                    questionId = 4L,
                    optionSelections = mutableListOf(false, true),
                    textResponse = "Updated answer"
                )
            )

            assertThat(existing.question).isSameAs(question)
            assertThat(existing.optionSelections).containsExactly(false, true)
            assertThat(existing.textResponse).isEqualTo("Updated answer")
            assertThat(result).isSameAs(existing)
        }
    }

    @Nested
    inner class FindAnswers {

        private val handler = FindAnswersHandler(answerService)

        @Test
        fun `returns all answers`() {
            val expected = mutableListOf(Answer())
            whenever(answerService.findAll()).thenReturn(expected)

            val result = handler.handle(FindAnswersCommand())

            assertThat(result).isSameAs(expected)
            verify(answerService).findAll()
        }
    }

    @Nested
    inner class FindAnswerById {

        private val handler = FindAnswerByIdHandler(answerService)

        @Test
        fun `returns answer by id`() {
            val expected = Answer()
            whenever(answerService.findById(9L)).thenReturn(expected)

            val result = handler.handle(FindAnswerByIdCommand(9L))

            assertThat(result).isSameAs(expected)
            verify(answerService).findById(9L)
        }
    }

    @Nested
    inner class FindAnswersBySurveyId {

        private val handler = FindAnswersBySurveyIdHandler(answerService)

        @Test
        fun `returns answers by survey id`() {
            val expected = mutableSetOf(Answer())
            whenever(answerService.findBySurveyId(10L)).thenReturn(expected)

            val result = handler.handle(FindAnswersBySurveyIdCommand(10L))

            assertThat(result).isSameAs(expected)
            verify(answerService).findBySurveyId(10L)
        }
    }

    @Nested
    inner class FindAnswersByQuestionId {

        private val handler = FindAnswersByQuestionIdHandler(answerService)

        @Test
        fun `returns answers by question id`() {
            val expected = mutableSetOf(Answer())
            whenever(answerService.findByQuestionId(11L)).thenReturn(expected)

            val result = handler.handle(FindAnswersByQuestionIdCommand(11L))

            assertThat(result).isSameAs(expected)
            verify(answerService).findByQuestionId(11L)
        }
    }

    @Nested
    inner class DeleteAnswerById {

        private val handler = DeleteAnswerByIdHandler(answerService)

        @Test
        fun `deletes answer by id`() {
            handler.handle(DeleteAnswerByIdCommand(12L))

            verify(answerService).deleteById(eq(12L))
        }
    }
}

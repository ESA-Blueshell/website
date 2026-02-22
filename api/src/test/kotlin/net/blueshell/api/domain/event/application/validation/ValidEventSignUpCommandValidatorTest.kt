package net.blueshell.api.domain.event.application.validation

import jakarta.validation.ConstraintValidatorContext
import net.blueshell.api.domain.committee.persistence.Committee
import net.blueshell.api.domain.event.application.EventService
import net.blueshell.api.domain.event.command.EventSignUpCandidate
import net.blueshell.api.domain.event.command.EventSignUpData
import net.blueshell.api.domain.event.persistence.Event
import net.blueshell.api.domain.survey.command.AnswerData
import net.blueshell.api.domain.survey.persistence.Question
import net.blueshell.api.domain.survey.persistence.Survey
import net.blueshell.api.shared.enums.QuestionType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.time.Instant

class ValidEventSignUpCommandValidatorTest {

    private val events = mock<EventService>()
    private val validator = ValidEventSignUpCommandValidator(events)

    @Test
    fun `accepts null candidate`() {
        assertThat(validator.isValid(null, mock())).isTrue()
    }

    @Test
    fun `rejects unknown event`() {
        whenever(events.findById(404)).thenThrow(RuntimeException("not found"))

        val context = mock<ConstraintValidatorContext>(defaultAnswer = Mockito.RETURNS_DEEP_STUBS)
        val candidate = TestCandidate(EventSignUpData(eventId = 404, answers = emptyList()))

        assertThat(validator.isValid(candidate, context)).isFalse()
    }

    @Test
    fun `accepts when event has no non-description questions`() {
        whenever(events.findById(1)).thenReturn(eventWithQuestions(
            question(1, QuestionType.DESCRIPTION)
        ))

        val candidate = TestCandidate(EventSignUpData(eventId = 1, answers = emptyList()))

        assertThat(validator.isValid(candidate, mock())).isTrue()
    }

    @Test
    fun `accepts valid answers matching form questions`() {
        whenever(events.findById(2)).thenReturn(eventWithQuestions(
            question(10, QuestionType.OPEN),
            question(11, QuestionType.RADIO),
            question(12, QuestionType.CHECKBOX),
            question(13, QuestionType.DESCRIPTION)
        ))

        val answers = listOf(
            AnswerData(questionId = 10),
            AnswerData(questionId = 11),
            AnswerData(questionId = 12)
        )
        val candidate = TestCandidate(EventSignUpData(eventId = 2, answers = answers))

        assertThat(validator.isValid(candidate, mock())).isTrue()
    }

    @Test
    fun `rejects answers with unknown question ids`() {
        whenever(events.findById(3)).thenReturn(eventWithQuestions(question(10, QuestionType.OPEN)))

        val candidate = TestCandidate(
            EventSignUpData(eventId = 3, answers = listOf(AnswerData(questionId = 999)))
        )

        val context = mock<ConstraintValidatorContext>(defaultAnswer = Mockito.RETURNS_DEEP_STUBS)
        assertThat(validator.isValid(candidate, context)).isFalse()
    }

    @Test
    fun `rejects duplicate question ids and missing required answers`() {
        whenever(events.findById(4)).thenReturn(eventWithQuestions(
            question(10, QuestionType.OPEN),
            question(11, QuestionType.RADIO)
        ))

        val candidate = TestCandidate(
            EventSignUpData(
                eventId = 4,
                answers = listOf(AnswerData(questionId = 10), AnswerData(questionId = 10))
            )
        )

        val context = mock<ConstraintValidatorContext>(defaultAnswer = Mockito.RETURNS_DEEP_STUBS)
        assertThat(validator.isValid(candidate, context)).isFalse()
    }

    private fun eventWithQuestions(vararg questions: Question): Event {
        val form = Survey()
        questions.forEach(form::addQuestion)

        return Event(
            committee = Committee(name = "Committee", description = "Description"),
            title = "Event",
            startTime = Instant.now(),
            endTime = Instant.now().plusSeconds(3600),
            signUp = true,
        ).apply {
            signUpForm = form
        }
    }

    private fun question(id: Long, type: QuestionType): Question {
        return Question(
            idx = 0L,
            survey = Survey(),
            type = type,
            label = "Question $id",
        ).apply {
            setEntityId(this, id)
        }
    }

    private fun setEntityId(entity: Any, id: Long) {
        val idField = generateSequence(entity.javaClass as Class<*>?) { it.superclass }
            .mapNotNull { runCatching { it.getDeclaredField("id") }.getOrNull() }
            .first()
        idField.isAccessible = true
        idField.set(entity, id)
    }

    private data class TestCandidate(
        override val data: EventSignUpData
    ) : EventSignUpCandidate
}

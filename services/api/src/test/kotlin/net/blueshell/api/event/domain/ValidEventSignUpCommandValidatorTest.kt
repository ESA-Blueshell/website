package net.blueshell.api.event.domain

import jakarta.validation.ConstraintValidatorContext
import net.blueshell.api.committee.persistence.Committee
import net.blueshell.api.event.persistence.Event
import net.blueshell.api.survey.api.AnswerData
import net.blueshell.api.survey.persistence.Question
import net.blueshell.api.survey.persistence.Survey
import net.blueshell.api.shared.enums.QuestionType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.time.Instant
import net.blueshell.api.event.api.EventService

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
        val candidate = EventSignUpData(eventId = 404, answers = emptyList())

        assertThat(validator.isValid(candidate, context)).isFalse()
    }

    @Test
    fun `accepts when event has no non-description questions`() {
        whenever(events.findById(1)).thenReturn(eventWithQuestions(
            question(1, QuestionType.DESCRIPTION)
        ))

        val candidate = EventSignUpData(eventId = 1, answers = emptyList())

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
        val candidate = EventSignUpData(eventId = 2, answers = answers)

        assertThat(validator.isValid(candidate, mock())).isTrue()
    }

    @Test
    fun `rejects answers with unknown question ids`() {
        whenever(events.findById(3)).thenReturn(eventWithQuestions(question(10, QuestionType.OPEN)))

        val candidate = 
            EventSignUpData(eventId = 3, answers = listOf(AnswerData(questionId = 999)))
        

        val context = mock<ConstraintValidatorContext>(defaultAnswer = Mockito.RETURNS_DEEP_STUBS)
        assertThat(validator.isValid(candidate, context)).isFalse()
    }

    @Test
    fun `rejects duplicate question ids and missing required answers`() {
        whenever(events.findById(4)).thenReturn(eventWithQuestions(
            question(10, QuestionType.OPEN),
            question(11, QuestionType.RADIO)
        ))

        val candidate = 
            EventSignUpData(
                eventId = 4,
                answers = listOf(AnswerData(questionId = 10), AnswerData(questionId = 10))
            )
        

        val context = mock<ConstraintValidatorContext>(defaultAnswer = Mockito.RETURNS_DEEP_STUBS)
        assertThat(validator.isValid(candidate, context)).isFalse()
    }

    // --- Deadline and capacity tests ---

    @Test
    fun `accepts signup when no deadline set`() {
        whenever(events.findById(10)).thenReturn(eventWithQuestions())

        val candidate = EventSignUpData(eventId = 10, answers = emptyList())

        assertThat(validator.isValid(candidate, mock())).isTrue()
    }

    @Test
    fun `accepts signup when before deadline`() {
        whenever(events.findById(11)).thenReturn(
            eventWithQuestions(signUpDeadline = Instant.now().plusSeconds(3600))
        )

        val candidate = EventSignUpData(eventId = 11, answers = emptyList())

        assertThat(validator.isValid(candidate, mock())).isTrue()
    }

    @Test
    fun `rejects signup when deadline has passed`() {
        whenever(events.findById(12)).thenReturn(
            eventWithQuestions(signUpDeadline = Instant.now().minusSeconds(1))
        )

        val candidate = EventSignUpData(eventId = 12, answers = emptyList())

        val context = mock<ConstraintValidatorContext>(defaultAnswer = Mockito.RETURNS_DEEP_STUBS)
        assertThat(validator.isValid(candidate, context)).isFalse()
    }

    @Test
    fun `accepts signup when no limit set`() {
        whenever(events.findById(13)).thenReturn(eventWithQuestions())

        val candidate = EventSignUpData(eventId = 13, answers = emptyList())

        assertThat(validator.isValid(candidate, mock())).isTrue()
    }

    @Test
    fun `accepts signup when under capacity`() {
        whenever(events.findById(14)).thenReturn(
            eventWithQuestions(signUpLimit = 5)
        )

        val candidate = EventSignUpData(eventId = 14, answers = emptyList())

        assertThat(validator.isValid(candidate, mock())).isTrue()
    }

    @Test
    fun `rejects signup when at capacity`() {
        whenever(events.findById(15)).thenReturn(
            eventWithQuestions(signUpLimit = 0, currentSignUpCount = 0)
        )

        val candidate = EventSignUpData(eventId = 15, answers = emptyList())

        val context = mock<ConstraintValidatorContext>(defaultAnswer = Mockito.RETURNS_DEEP_STUBS)
        assertThat(validator.isValid(candidate, context)).isFalse()
    }

    @Test
    fun `rejects when both deadline passed and at capacity`() {
        whenever(events.findById(16)).thenReturn(
            eventWithQuestions(
                signUpDeadline = Instant.now().minusSeconds(1),
                signUpLimit = 0,
                currentSignUpCount = 0
            )
        )

        val candidate = EventSignUpData(eventId = 16, answers = emptyList())

        val context = mock<ConstraintValidatorContext>(defaultAnswer = Mockito.RETURNS_DEEP_STUBS)
        assertThat(validator.isValid(candidate, context)).isFalse()
    }

    private fun eventWithQuestions(
        vararg questions: Question,
        signUpDeadline: Instant? = null,
        signUpLimit: Int? = null,
        currentSignUpCount: Long = 0
    ): Event {
        val form = Survey()
        questions.forEach(form::addQuestion)

        val event = Event(
            committee = Committee(name = "Committee", description = "Description"),
            title = "Event",
            startTime = Instant.now(),
            endTime = Instant.now().plusSeconds(3600),
            signUp = true,
            signUpDeadline = signUpDeadline,
            signUpLimit = signUpLimit,
        ).apply {
            signUpForm = form
        }

        if (currentSignUpCount > 0) {
            val countField = generateSequence(event.javaClass as Class<*>?) { it.superclass }
                .mapNotNull { runCatching { it.getDeclaredField("signUpCount") }.getOrNull() }
                .first()
            countField.isAccessible = true
            countField.set(event, currentSignUpCount)
        }

        return event
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

}

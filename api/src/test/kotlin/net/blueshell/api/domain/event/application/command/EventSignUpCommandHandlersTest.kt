package net.blueshell.api.domain.event.application.command

import net.blueshell.api.domain.event.application.EventSignUpService
import net.blueshell.api.domain.event.application.query.EventSignUpQuery
import net.blueshell.api.domain.event.command.CreateEventSignUpCommand
import net.blueshell.api.domain.event.command.DeleteEventSignUpCommand
import net.blueshell.api.domain.event.command.EventSignUpData
import net.blueshell.api.domain.event.command.FindEventSignUpsByAccessTokenCommand
import net.blueshell.api.domain.event.command.FindEventSignUpsByEventIdCommand
import net.blueshell.api.domain.event.command.FindEventSignUpsCommand
import net.blueshell.api.domain.event.command.GuestData
import net.blueshell.api.domain.event.command.UpdateEventSignUpCommand
import net.blueshell.api.domain.event.persistence.Event
import net.blueshell.api.domain.event.persistence.EventSignUp
import net.blueshell.api.domain.event.persistence.repository.EventRepository
import net.blueshell.api.domain.survey.application.QuestionService
import net.blueshell.api.domain.survey.command.AnswerData
import net.blueshell.api.domain.survey.persistence.Question
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class EventSignUpCommandHandlersTest {

    private val eventSignUpService = mock<EventSignUpService>()
    private val eventRepository = mock<EventRepository>()
    private val questionService = mock<QuestionService>()

    @Nested
    inner class FindEventSignUps {

        private val handler = FindEventSignUpsHandler(eventSignUpService)

        @Test
        fun `returns sign ups by filter`() {
            val filter = EventSignUpQuery(eventId = 5L)
            val expected = mutableListOf(emptySignUp())
            whenever(eventSignUpService.findByFilter(filter)).thenReturn(expected)

            val result = handler.handle(FindEventSignUpsCommand(filter))

            assertThat(result).isSameAs(expected)
            verify(eventSignUpService).findByFilter(filter)
        }
    }

    @Nested
    inner class FindEventSignUpsByAccessToken {

        private val handler = FindEventSignUpsByAccessTokenHandler(eventSignUpService)

        @Test
        fun `returns sign ups by guest access token`() {
            val expected = mutableListOf(emptySignUp())
            whenever(eventSignUpService.findByGuestAccessToken("TOKEN-1")).thenReturn(expected)

            val result = handler.handle(FindEventSignUpsByAccessTokenCommand("TOKEN-1"))

            assertThat(result).isSameAs(expected)
            verify(eventSignUpService).findByGuestAccessToken("TOKEN-1")
        }
    }

    @Nested
    inner class FindEventSignUpsByEventId {

        private val handler = FindEventSignUpsByEventIdHandler(eventSignUpService)

        @Test
        fun `returns sign ups by event id`() {
            val expected = mutableListOf(emptySignUp())
            whenever(eventSignUpService.findByEventId(8L)).thenReturn(expected)

            val result = handler.handle(FindEventSignUpsByEventIdCommand(8L))

            assertThat(result).isSameAs(expected)
            verify(eventSignUpService).findByEventId(8L)
        }
    }

    @Nested
    inner class CreateEventSignUp {

        private val handler = CreateEventSignUpHandler(eventSignUpService, eventRepository, questionService)

        @Test
        fun `creates sign up and overrides user id with principal id`() {
            val eventRef = mock<Event>()
            val questionRef = mock<Question>()
            whenever(eventRepository.getReferenceById(100L)).thenReturn(eventRef)
            whenever(questionService.getReferenceById(200L)).thenReturn(questionRef)
            val captured = argumentCaptor<EventSignUp>()
            whenever(eventSignUpService.create(captured.capture())).thenAnswer { captured.firstValue }
            val command = CreateEventSignUpCommand(
                data = EventSignUpData(
                    eventId = 100L,
                    answers = listOf(
                        AnswerData(
                            questionId = 200L,
                            optionSelections = listOf(true, false),
                            textResponse = "Because",
                            version = 3L
                        )
                    ),
                    guest = GuestData(
                        name = "Guest",
                        email = "guest@example.com",
                        discord = "guest#0001",
                        phoneNumber = "0612345678",
                        accessToken = "GUEST-TOKEN",
                        version = 2L
                    ),
                    userId = 5L,
                    version = 7L
                ),
                principalId = 42L
            )

            val result = handler.handle(command)

            assertThat(captured.firstValue.event).isSameAs(eventRef)
            assertThat(captured.firstValue.userId).isEqualTo(42L)
            assertThat(captured.firstValue.version).isEqualTo(7L)
            assertThat(captured.firstValue.guest?.accessToken).isEqualTo("GUEST-TOKEN")
            assertThat(captured.firstValue.answers).hasSize(1)
            assertThat(captured.firstValue.answers.first().question).isSameAs(questionRef)
            assertThat(captured.firstValue.answers.first().optionSelections).containsExactly(true, false)
            assertThat(captured.firstValue.answers.first().textResponse).isEqualTo("Because")
            assertThat(result).isSameAs(captured.firstValue)
        }

        @Test
        fun `generates guest access token when missing`() {
            val eventRef = mock<Event>()
            whenever(eventRepository.getReferenceById(101L)).thenReturn(eventRef)
            val captured = argumentCaptor<EventSignUp>()
            whenever(eventSignUpService.create(captured.capture())).thenAnswer { captured.firstValue }
            val command = CreateEventSignUpCommand(
                data = EventSignUpData(
                    eventId = 101L,
                    answers = emptyList(),
                    guest = GuestData(
                        name = "Guest",
                        email = "guest2@example.com",
                        discord = "guest#0002",
                        phoneNumber = "0687654321",
                        accessToken = null,
                        version = null
                    ),
                    userId = null,
                    version = null
                ),
                principalId = null
            )

            val result = handler.handle(command)

            assertThat(result.guest?.accessToken).isNotBlank().hasSize(30)
        }
    }

    @Nested
    inner class UpdateEventSignUp {

        private val handler = UpdateEventSignUpHandler(eventSignUpService, eventRepository, questionService)

        @Test
        fun `updates sign up resolved by principal when access token is missing`() {
            val existing = emptySignUp()
            val eventRef = mock<Event>()
            val questionRef = mock<Question>()
            whenever(eventSignUpService.findByUserIdAndEventId(42L, 100L)).thenReturn(existing)
            whenever(eventRepository.getReferenceById(100L)).thenReturn(eventRef)
            whenever(questionService.getReferenceById(201L)).thenReturn(questionRef)
            whenever(eventSignUpService.update(existing)).thenReturn(existing)

            val result = handler.handle(
                UpdateEventSignUpCommand(
                    eventId = 100L,
                    data = EventSignUpData(
                        eventId = 100L,
                        answers = listOf(AnswerData(questionId = 201L, textResponse = "Updated")),
                        userId = 55L,
                        guest = null,
                        version = 4L
                    ),
                    accessToken = null,
                    principalId = 42L
                )
            )

            verify(eventSignUpService).findByUserIdAndEventId(42L, 100L)
            assertThat(existing.event).isSameAs(eventRef)
            assertThat(existing.userId).isEqualTo(55L)
            assertThat(existing.version).isEqualTo(4L)
            assertThat(existing.answers).hasSize(1)
            assertThat(existing.answers.first().question).isSameAs(questionRef)
            assertThat(existing.answers.first().textResponse).isEqualTo("Updated")
            assertThat(result).isSameAs(existing)
        }

        @Test
        fun `updates sign up resolved by guest access token`() {
            val existing = emptySignUp()
            val eventRef = mock<Event>()
            whenever(eventSignUpService.findByGuestAccessTokenAndEventId("TOKEN-2", 101L)).thenReturn(existing)
            whenever(eventRepository.getReferenceById(101L)).thenReturn(eventRef)
            whenever(eventSignUpService.update(existing)).thenReturn(existing)

            handler.handle(
                UpdateEventSignUpCommand(
                    eventId = 101L,
                    data = EventSignUpData(
                        eventId = 101L,
                        answers = emptyList(),
                        guest = null,
                        userId = null,
                        version = null
                    ),
                    accessToken = "TOKEN-2",
                    principalId = null
                )
            )

            verify(eventSignUpService).findByGuestAccessTokenAndEventId("TOKEN-2", 101L)
            verify(eventSignUpService).update(existing)
            assertThat(existing.event).isSameAs(eventRef)
        }

        @Test
        fun `throws when neither access token nor principal is provided`() {
            assertThatThrownBy {
                handler.handle(
                    UpdateEventSignUpCommand(
                        eventId = 100L,
                        data = EventSignUpData(eventId = 100L),
                        accessToken = null,
                        principalId = null
                    )
                )
            }.isInstanceOf(IllegalArgumentException::class.java)
                .hasMessage("User must be authenticated")
        }
    }

    @Nested
    inner class DeleteEventSignUp {

        private val handler = DeleteEventSignUpHandler(eventSignUpService)

        @Test
        fun `deletes sign up by id`() {
            handler.handle(DeleteEventSignUpCommand(eventSignUpId = 33L))

            verify(eventSignUpService).deleteById(eq(33L))
        }
    }

    private fun emptySignUp(): EventSignUp = EventSignUp(event = mock())
}

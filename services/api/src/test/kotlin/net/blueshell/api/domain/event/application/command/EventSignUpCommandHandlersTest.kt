package net.blueshell.api.domain.event.application.command

import net.blueshell.api.domain.event.application.EventSignUpService
import net.blueshell.api.domain.event.application.GuestService
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
import net.blueshell.api.domain.event.persistence.Guest
import net.blueshell.api.domain.event.persistence.repository.EventRepository
import net.blueshell.api.domain.survey.application.QuestionService
import net.blueshell.api.domain.survey.command.AnswerData
import net.blueshell.api.domain.survey.persistence.Question
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class EventSignUpCommandHandlersTest {

    private val eventSignUpService = mock<EventSignUpService>()
    private val guestService = mock<GuestService>()
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
            assertThat(captured.firstValue.guest?.accessTokenRaw).isEqualTo("GUEST-TOKEN")
            assertThat(captured.firstValue.guest?.matchesAccessToken("GUEST-TOKEN")).isTrue()
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

            val rawToken = result.guest?.accessTokenRaw
            assertThat(rawToken).isNotBlank()
            assertThat(result.guest?.matchesAccessToken(rawToken!!)).isTrue()
        }

        @Test
        fun `anonymous create strips spoofed user id`() {
            val eventRef = mock<Event>()
            whenever(eventRepository.getReferenceById(102L)).thenReturn(eventRef)
            val captured = argumentCaptor<EventSignUp>()
            whenever(eventSignUpService.create(captured.capture())).thenAnswer { captured.firstValue }

            val command = CreateEventSignUpCommand(
                data = EventSignUpData(
                    eventId = 102L,
                    answers = emptyList(),
                    guest = GuestData(
                        name = "Guest",
                        email = "guest3@example.com",
                        discord = "guest#0003",
                        phoneNumber = "0611111111",
                        accessToken = "TOKEN-3",
                        version = null
                    ),
                    userId = 999L,
                    version = null
                ),
                principalId = null
            )

            val result = handler.handle(command)

            assertThat(result.userId).isNull()
        }

        @Test
        fun `anonymous create without guest is rejected`() {
            val command = CreateEventSignUpCommand(
                data = EventSignUpData(
                    eventId = 103L,
                    answers = emptyList(),
                    guest = null,
                    userId = null,
                    version = null
                ),
                principalId = null
            )

            assertThatThrownBy { handler.handle(command) }
                .isInstanceOfSatisfying(ResponseStatusException::class.java) { ex ->
                    assertThat(ex.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
                }
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
            assertThat(existing.userId).isEqualTo(42L)
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
            assertThat(existing.userId).isNull()
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

        private val handler = DeleteEventSignUpHandler(eventSignUpService, guestService)

        @Test
        fun `deletes sign up by id when no guest access token is supplied`() {
            handler.handle(DeleteEventSignUpCommand(eventSignUpId = 33L))

            verify(eventSignUpService).deleteById(eq(33L))
        }

        @Test
        fun `deletes sign up when guest token matches target signup`() {
            val signUp = emptySignUp().apply {
                guest = Guest.withRawToken(
                    name = "Guest",
                    discord = "guest#0001",
                    email = "guest-delete@example.com",
                    accessToken = "MATCHING-TOKEN",
                    phoneNumber = "0612345678"
                )
            }
            whenever(guestService.findByAccessToken("MATCHING-TOKEN")).thenReturn(signUp.guest!!)
            whenever(eventSignUpService.findById(34L)).thenReturn(signUp)

            handler.handle(DeleteEventSignUpCommand(eventSignUpId = 34L, accessToken = "MATCHING-TOKEN"))

            verify(guestService).findByAccessToken("MATCHING-TOKEN")
            verify(eventSignUpService).findById(34L)
            verify(eventSignUpService).delete(signUp)
            verify(eventSignUpService, never()).deleteById(eq(34L))
        }

        @Test
        fun `rejects delete when guest token does not belong to target signup`() {
            val signUp = emptySignUp().apply {
                guest = Guest.withRawToken(
                    name = "Guest",
                    discord = "guest#0001",
                    email = "guest-mismatch@example.com",
                    accessToken = "REAL-TOKEN",
                    phoneNumber = "0612345678"
                )
            }
            whenever(guestService.findByAccessToken("WRONG-TOKEN")).thenReturn(
                Guest.withRawToken(
                    name = "Other Guest",
                    discord = "guest#0002",
                    email = "guest-other@example.com",
                    accessToken = "WRONG-TOKEN",
                    phoneNumber = "0612345678"
                )
            )
            whenever(eventSignUpService.findById(35L)).thenReturn(signUp)

            assertThatThrownBy {
                handler.handle(DeleteEventSignUpCommand(eventSignUpId = 35L, accessToken = "WRONG-TOKEN"))
            }
                .isInstanceOfSatisfying(ResponseStatusException::class.java) { ex ->
                    assertThat(ex.statusCode).isEqualTo(HttpStatus.FORBIDDEN)
                    assertThat(ex.reason).contains("does not match")
                }

            verify(guestService).findByAccessToken("WRONG-TOKEN")
            verify(eventSignUpService).findById(35L)
            verify(eventSignUpService, never()).delete(signUp)
            verify(eventSignUpService, never()).deleteById(eq(35L))
        }

        @Test
        fun `rejects delete when guest token is unknown`() {
            whenever(guestService.findByAccessToken("UNKNOWN-TOKEN")).thenThrow(
                ResponseStatusException(HttpStatus.NOT_FOUND, "Guest not found")
            )

            assertThatThrownBy {
                handler.handle(DeleteEventSignUpCommand(eventSignUpId = 36L, accessToken = "UNKNOWN-TOKEN"))
            }
                .isInstanceOfSatisfying(ResponseStatusException::class.java) { ex ->
                    assertThat(ex.statusCode).isEqualTo(HttpStatus.NOT_FOUND)
                }

            verify(guestService).findByAccessToken("UNKNOWN-TOKEN")
            verify(eventSignUpService, never()).findById(eq(36L))
            verify(eventSignUpService, never()).deleteById(eq(36L))
        }
    }

    private fun emptySignUp(): EventSignUp = EventSignUp(event = mock())
}

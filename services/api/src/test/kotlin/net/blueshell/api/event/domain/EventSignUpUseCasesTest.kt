package net.blueshell.api.event.domain

import jakarta.validation.ConstraintViolation
import jakarta.validation.ConstraintViolationException
import jakarta.validation.Validator
import net.blueshell.api.event.persistence.Event
import net.blueshell.api.event.persistence.EventRepository
import net.blueshell.api.event.persistence.EventSignUp
import net.blueshell.api.event.persistence.Guest
import net.blueshell.api.shared.job.EmailJobs
import net.blueshell.api.shared.job.JobQueue
import net.blueshell.api.shared.security.CurrentUser
import net.blueshell.api.shared.security.CurrentUserProvider
import net.blueshell.api.survey.api.AnswerData
import net.blueshell.api.survey.api.QuestionService
import net.blueshell.api.survey.persistence.Question
import net.blueshell.api.shared.enums.Role
import net.blueshell.api.user.api.UserService
import net.blueshell.api.user.persistence.User
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException

class EventSignUpUseCasesTest {
    private val eventSignUpService = mock<EventSignUpService>()
    private val guestService = mock<GuestService>()
    private val eventRepository = mock<EventRepository>()
    private val questionService = mock<QuestionService>()
    private val validator = mock<Validator>()
    private val jobs = mock<JobQueue>()
    private val users = mock<UserService>()
    private val currentUser =
        mock<CurrentUserProvider> {
            on { currentUser() } doReturn CurrentUser(id = 1L, roles = setOf(Role.BOARD), addressId = null)
        }
    private val useCases =
        EventSignUpUseCases(
            eventSignUpService,
            eventRepository,
            questionService,
            guestService,
            validator,
            jobs,
            users,
            currentUser,
        )

    private fun account(
        id: Long,
        vararg roles: Role,
    ): User =
        User(
            username = "user$id",
            email = "user$id@example.com",
            password = "hashed",
            initials = "U.",
            firstName = "User",
            lastName = "$id",
            roles = roles.toMutableSet(),
        ).also { it.id = id }

    @Nested
    inner class CreateEventSignUp {
        @Test
        fun `creates sign up and overrides user id with principal id`() {
            val eventRef = mock<Event>()
            val questionRef = mock<Question>()
            whenever(eventRepository.getReferenceById(100L)).thenReturn(eventRef)
            whenever(questionService.getReferenceById(200L)).thenReturn(questionRef)
            val captured = argumentCaptor<EventSignUp>()
            whenever(eventSignUpService.create(captured.capture())).thenAnswer { captured.firstValue }

            val result =
                useCases.create(
                    EventSignUpData(
                        eventId = 100L,
                        answers =
                            listOf(
                                AnswerData(
                                    questionId = 200L,
                                    optionSelections = listOf(true, false),
                                    textResponse = "Because",
                                    version = 3L,
                                ),
                            ),
                        guest =
                            GuestData(
                                name = "Guest",
                                email = "guest@example.com",
                                discord = "guest#0001",
                                phoneNumber = "0612345678",
                                accessToken = "GUEST-TOKEN",
                                version = 2L,
                            ),
                        userId = 5L,
                        version = 7L,
                    ),
                    42L,
                )

            assertThat(captured.firstValue.event).isSameAs(eventRef)
            assertThat(captured.firstValue.userId).isEqualTo(42L)
            assertThat(captured.firstValue.version).isEqualTo(7L)
            assertThat(captured.firstValue.guest?.accessTokenRaw).isEqualTo("GUEST-TOKEN")
            assertThat(captured.firstValue.guest?.matchesAccessToken("GUEST-TOKEN")).isTrue()
            assertThat(captured.firstValue.answers).hasSize(1)
            assertThat(
                captured.firstValue.answers
                    .first()
                    .question,
            ).isSameAs(questionRef)
            assertThat(
                captured.firstValue.answers
                    .first()
                    .optionSelections,
            ).containsExactly(true, false)
            assertThat(
                captured.firstValue.answers
                    .first()
                    .textResponse,
            ).isEqualTo("Because")
            assertThat(result).isSameAs(captured.firstValue)
        }

        @Test
        fun `generates guest access token when missing`() {
            val eventRef = mock<Event>()
            whenever(eventRepository.getReferenceById(101L)).thenReturn(eventRef)
            val captured = argumentCaptor<EventSignUp>()
            whenever(eventSignUpService.create(captured.capture())).thenAnswer { captured.firstValue }

            val result =
                useCases.create(
                    EventSignUpData(
                        eventId = 101L,
                        answers = emptyList(),
                        guest =
                            GuestData(
                                name = "Guest",
                                email = "guest2@example.com",
                                discord = "guest#0002",
                                phoneNumber = "0687654321",
                                accessToken = null,
                                version = null,
                            ),
                        userId = null,
                        version = null,
                    ),
                    null,
                )

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

            val result =
                useCases.create(
                    EventSignUpData(
                        eventId = 102L,
                        answers = emptyList(),
                        guest =
                            GuestData(
                                name = "Guest",
                                email = "guest3@example.com",
                                discord = "guest#0003",
                                phoneNumber = "0611111111",
                                accessToken = "TOKEN-3",
                                version = null,
                            ),
                        userId = 999L,
                        version = null,
                    ),
                    null,
                )

            assertThat(result.userId).isNull()
        }

        @Test
        fun `anonymous create without guest is rejected`() {
            assertThatThrownBy {
                useCases.create(
                    EventSignUpData(
                        eventId = 103L,
                        answers = emptyList(),
                        guest = null,
                        userId = null,
                        version = null,
                    ),
                    null,
                )
            }.isInstanceOfSatisfying(ResponseStatusException::class.java) { ex ->
                assertThat(ex.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
            }
        }
    }

    @Nested
    inner class UpdateEventSignUp {
        @Test
        fun `updates sign up resolved by principal when access token is missing`() {
            val existing = emptySignUp()
            val eventRef = mock<Event>()
            val questionRef = mock<Question>()
            whenever(eventSignUpService.findByUserIdAndEventId(42L, 100L)).thenReturn(existing)
            whenever(eventRepository.getReferenceById(100L)).thenReturn(eventRef)
            whenever(questionService.getReferenceById(201L)).thenReturn(questionRef)
            whenever(eventSignUpService.update(existing)).thenReturn(existing)

            val result =
                useCases.update(
                    100L,
                    EventSignUpData(
                        eventId = 100L,
                        answers = listOf(AnswerData(questionId = 201L, textResponse = "Updated")),
                        userId = 55L,
                        guest = null,
                        version = 4L,
                    ),
                    42L,
                    null,
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

            useCases.update(
                101L,
                EventSignUpData(
                    eventId = 101L,
                    answers = emptyList(),
                    guest = null,
                    userId = null,
                    version = null,
                ),
                null,
                "TOKEN-2",
            )

            verify(eventSignUpService).findByGuestAccessTokenAndEventId("TOKEN-2", 101L)
            verify(eventSignUpService).update(existing)
            assertThat(existing.event).isSameAs(eventRef)
            assertThat(existing.userId).isNull()
        }

        @Test
        fun `throws when neither access token nor principal is provided`() {
            assertThatThrownBy {
                useCases.update(100L, EventSignUpData(eventId = 100L), null, null)
            }.isInstanceOf(IllegalArgumentException::class.java)
                .hasMessage("User must be authenticated")
        }
    }

    @Nested
    inner class UpdateEventSignUpById {
        @Test
        fun `applies the answers a board member typed onto the sign-up they picked`() {
            val eventRef = mock<Event>()
            whenever(eventRef.id).thenReturn(100L)
            whenever(eventRepository.getReferenceById(100L)).thenReturn(eventRef)
            val questionRef = mock<Question>()
            whenever(questionService.getReferenceById(200L)).thenReturn(questionRef)
            val signUp = EventSignUp(event = eventRef, userId = 7L)
            whenever(eventSignUpService.findById(40L)).thenReturn(signUp)
            whenever(eventSignUpService.update(signUp)).thenReturn(signUp)

            val result =
                useCases.updateById(
                    40L,
                    EventSignUpData(
                        eventId = 0L,
                        answers = listOf(AnswerData(questionId = 200L, textResponse = "Rewritten")),
                        version = 3L,
                    ),
                )

            assertThat(result.answers).hasSize(1)
            assertThat(result.userId).isEqualTo(7L)
            verify(eventSignUpService).update(signUp)
        }

        @Test
        fun `edits the guest's own details on a guest sign-up`() {
            val eventRef = mock<Event>()
            whenever(eventRef.id).thenReturn(100L)
            whenever(eventRepository.getReferenceById(100L)).thenReturn(eventRef)
            val signUp =
                EventSignUp(event = eventRef).apply {
                    guest =
                        Guest.withRawToken(
                            name = "Typo",
                            discord = "typo#0001",
                            email = "typo@example.com",
                            accessToken = "TOKEN",
                            phoneNumber = "0611111111",
                        )
                }
            whenever(eventSignUpService.findById(41L)).thenReturn(signUp)
            whenever(eventSignUpService.update(signUp)).thenReturn(signUp)

            useCases.updateById(
                41L,
                EventSignUpData(
                    eventId = 0L,
                    guest =
                        GuestData(
                            name = "Fixed Name",
                            email = "fixed@example.com",
                            discord = "fixed#0002",
                            phoneNumber = "0622222222",
                        ),
                ),
            )

            assertThat(signUp.guest?.name).isEqualTo("Fixed Name")
            assertThat(signUp.guest?.email).isEqualTo("fixed@example.com")
            assertThat(signUp.guest?.discord).isEqualTo("fixed#0002")
            assertThat(signUp.guest?.phoneNumber).isEqualTo("0622222222")
        }

        @Test
        fun `keeps the guest when the body carries no guest details`() {
            val eventRef = mock<Event>()
            whenever(eventRef.id).thenReturn(100L)
            whenever(eventRepository.getReferenceById(100L)).thenReturn(eventRef)
            val guest =
                Guest.withRawToken(
                    name = "Guest",
                    discord = "guest#0001",
                    email = "guest@example.com",
                    accessToken = "TOKEN",
                    phoneNumber = "0611111111",
                )
            val signUp = EventSignUp(event = eventRef).apply { this.guest = guest }
            whenever(eventSignUpService.findById(42L)).thenReturn(signUp)
            whenever(eventSignUpService.update(signUp)).thenReturn(signUp)

            useCases.updateById(42L, EventSignUpData(eventId = 0L))

            assertThat(signUp.guest).isSameAs(guest)
        }

        @Test
        fun `a guest with no phone number keeps the guest, and the phone reads as empty`() {
            val eventRef = mock<Event>()
            whenever(eventRef.id).thenReturn(100L)
            whenever(eventRepository.getReferenceById(100L)).thenReturn(eventRef)
            val guest =
                Guest.withRawToken(
                    name = "Phoneless",
                    discord = "phoneless#0001",
                    email = "phoneless@example.com",
                    accessToken = "TOKEN",
                )
            val signUp = EventSignUp(event = eventRef).apply { this.guest = guest }
            whenever(eventSignUpService.findById(44L)).thenReturn(signUp)
            whenever(eventSignUpService.update(signUp)).thenReturn(signUp)

            useCases.updateById(44L, EventSignUpData(eventId = 0L))

            assertThat(signUp.guest).isSameAs(guest)
            assertThat(signUp.guest?.phoneNumber).isEmpty()
        }

        @Test
        fun `ignores guest details sent for an account sign-up`() {
            val eventRef = mock<Event>()
            whenever(eventRef.id).thenReturn(100L)
            whenever(eventRepository.getReferenceById(100L)).thenReturn(eventRef)
            val signUp = EventSignUp(event = eventRef, userId = 7L)
            whenever(eventSignUpService.findById(43L)).thenReturn(signUp)
            whenever(eventSignUpService.update(signUp)).thenReturn(signUp)

            useCases.updateById(
                43L,
                EventSignUpData(
                    eventId = 0L,
                    guest =
                        GuestData(
                            name = "Not This",
                            email = "not-this@example.com",
                            discord = "no#0001",
                            phoneNumber = "0600000000",
                        ),
                ),
            )

            assertThat(signUp.guest).isNull()
            assertThat(signUp.userId).isEqualTo(7L)
        }
    }

    @Nested
    inner class ReassignGuestSignUp {
        private fun guestSignUp(
            eventId: Long = 100L,
            membersOnly: Boolean = false,
        ): Pair<Event, EventSignUp> {
            val event = mock<Event>()
            whenever(event.id).thenReturn(eventId)
            whenever(event.membersOnly).thenReturn(membersOnly)
            whenever(eventRepository.getReferenceById(eventId)).thenReturn(event)
            val signUp =
                EventSignUp(event = event).apply {
                    guest =
                        Guest.withRawToken(
                            name = "Guest Gordon",
                            discord = "gordon#0001",
                            email = "gordon@example.com",
                            accessToken = "TOKEN",
                            phoneNumber = "0611111111",
                        )
                }
            return event to signUp
        }

        @Test
        fun `moves a guest sign-up onto the account and retires the guest`() {
            val (_, signUp) = guestSignUp()
            val guest = signUp.guest!!
            whenever(eventSignUpService.findById(50L)).thenReturn(signUp)
            whenever(eventSignUpService.update(signUp)).thenReturn(signUp)
            whenever(users.findById(9L)).thenReturn(account(9L, Role.MEMBER))
            whenever(eventSignUpService.existsByUserIdAndEventId(9L, 100L)).thenReturn(false)

            useCases.updateById(50L, EventSignUpData(eventId = 0L, userId = 9L))

            assertThat(signUp.userId).isEqualTo(9L)
            assertThat(signUp.guest).isNull()
            verify(guestService).delete(guest)
        }

        @Test
        fun `refuses when the account already signed up for that event`() {
            val (_, signUp) = guestSignUp()
            whenever(eventSignUpService.findById(51L)).thenReturn(signUp)
            whenever(users.findById(9L)).thenReturn(account(9L, Role.MEMBER))
            whenever(eventSignUpService.existsByUserIdAndEventId(9L, 100L)).thenReturn(true)

            assertThatThrownBy { useCases.updateById(51L, EventSignUpData(eventId = 0L, userId = 9L)) }
                .isInstanceOf(ResponseStatusException::class.java)
                .extracting { (it as ResponseStatusException).statusCode }
                .isEqualTo(HttpStatus.CONFLICT)
        }

        @Test
        fun `refuses a non-member on a members-only event`() {
            val (_, signUp) = guestSignUp(membersOnly = true)
            whenever(eventSignUpService.findById(52L)).thenReturn(signUp)
            whenever(users.findById(9L)).thenReturn(account(9L, Role.GUEST))

            assertThatThrownBy { useCases.updateById(52L, EventSignUpData(eventId = 0L, userId = 9L)) }
                .isInstanceOf(ResponseStatusException::class.java)
                .extracting { (it as ResponseStatusException).statusCode }
                .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY)
        }

        @Test
        fun `lets an inherited membership onto a members-only event`() {
            val (_, signUp) = guestSignUp(membersOnly = true)
            whenever(eventSignUpService.findById(53L)).thenReturn(signUp)
            whenever(eventSignUpService.update(signUp)).thenReturn(signUp)
            whenever(users.findById(9L)).thenReturn(account(9L, Role.BOARD))
            whenever(eventSignUpService.existsByUserIdAndEventId(9L, 100L)).thenReturn(false)

            useCases.updateById(53L, EventSignUpData(eventId = 0L, userId = 9L))

            assertThat(signUp.userId).isEqualTo(9L)
        }

        @Test
        fun `a sign-up with neither holder is not moved either`() {
            val event = mock<Event>()
            whenever(event.id).thenReturn(100L)
            whenever(eventRepository.getReferenceById(100L)).thenReturn(event)
            val signUp = EventSignUp(event = event)
            whenever(eventSignUpService.findById(55L)).thenReturn(signUp)
            whenever(eventSignUpService.update(signUp)).thenReturn(signUp)

            useCases.updateById(55L, EventSignUpData(eventId = 0L, userId = 9L))

            assertThat(signUp.userId).isNull()
            verifyNoInteractions(users)
        }

        @Test
        fun `leaves an account sign-up where it is`() {
            val event = mock<Event>()
            whenever(event.id).thenReturn(100L)
            whenever(eventRepository.getReferenceById(100L)).thenReturn(event)
            val signUp = EventSignUp(event = event, userId = 7L)
            whenever(eventSignUpService.findById(54L)).thenReturn(signUp)
            whenever(eventSignUpService.update(signUp)).thenReturn(signUp)

            useCases.updateById(54L, EventSignUpData(eventId = 0L, userId = 9L))

            assertThat(signUp.userId).isEqualTo(7L)
            verifyNoInteractions(users)
        }
    }

    @Nested
    inner class RefusedByValidation {
        @Test
        fun `a board edit the validator refuses does not reach the store`() {
            val eventRef = mock<Event>()
            whenever(eventRef.id).thenReturn(100L)
            whenever(eventRepository.getReferenceById(100L)).thenReturn(eventRef)
            val signUp = EventSignUp(event = eventRef, userId = 7L)
            whenever(eventSignUpService.findById(45L)).thenReturn(signUp)
            whenever(validator.validate(any<EventSignUpData>()))
                .thenReturn(setOf(mock<ConstraintViolation<EventSignUpData>>()))

            assertThatThrownBy { useCases.updateById(45L, EventSignUpData(eventId = 0L)) }
                .isInstanceOf(ConstraintViolationException::class.java)

            verify(eventSignUpService, never()).update(any())
        }
    }

    @Nested
    inner class DeleteEventSignUp {
        @Test
        fun `deletes sign up by id when no guest access token is supplied`() {
            useCases.delete(33L, null)

            verify(eventSignUpService).deleteById(eq(33L))
            verifyNoInteractions(jobs)
        }

        @Test
        fun `tells a guest their sign up is removed when asked to`() {
            val event = mock<Event>()
            whenever(event.title).thenReturn("LAN Party")
            val signUp =
                EventSignUp(event = event).apply {
                    guest =
                        Guest.withRawToken(
                            name = "Guest Gordon",
                            discord = "guest#0001",
                            email = "gordon@example.com",
                            accessToken = "TOKEN",
                        )
                }
            whenever(eventSignUpService.findById(35L)).thenReturn(signUp)

            useCases.delete(35L, null, notify = true)

            verify(eventSignUpService).delete(signUp)
            verify(jobs).runAsync(
                eq(EmailJobs.EventSignUpRemoved),
                eq(
                    EmailJobs.EventSignUpRemovedPayload(
                        recipientEmail = "gordon@example.com",
                        recipientName = "Guest Gordon",
                        eventTitle = "LAN Party",
                    ),
                ),
            )
        }

        @Test
        fun `tells an account holder at the address on their account`() {
            val event = mock<Event>()
            whenever(event.title).thenReturn("LAN Party")
            val user =
                User(
                    username = "ada",
                    email = "ada@example.com",
                    password = "hashed",
                    initials = "A.",
                    firstName = "Ada",
                    lastName = "Lovelace",
                )
            val signUp = EventSignUp(event = event).apply { this.user = user }
            whenever(eventSignUpService.findById(36L)).thenReturn(signUp)

            useCases.delete(36L, null, notify = true)

            verify(jobs).runAsync(
                eq(EmailJobs.EventSignUpRemoved),
                eq(
                    EmailJobs.EventSignUpRemovedPayload(
                        recipientEmail = "ada@example.com",
                        recipientName = "Ada Lovelace",
                        eventTitle = "LAN Party",
                    ),
                ),
            )
        }

        @Test
        fun `a member removing their own sign up tells nobody, even asking for it`() {
            whenever(currentUser.currentUser())
                .thenReturn(CurrentUser(id = 4L, roles = setOf(Role.MEMBER), addressId = null))

            useCases.delete(38L, null, notify = true)

            verify(eventSignUpService).deleteById(eq(38L))
            verifyNoInteractions(jobs)
        }

        @Test
        fun `a sign-up naming nobody is removed without an email`() {
            val event = mock<Event>()
            whenever(eventSignUpService.findById(39L)).thenReturn(EventSignUp(event = event))

            useCases.delete(39L, null, notify = true)

            verify(eventSignUpService).delete(any())
            verifyNoInteractions(jobs)
        }

        @Test
        fun `no caller at all is not a board caller`() {
            whenever(currentUser.currentUser()).thenReturn(null)

            useCases.delete(40L, null, notify = true)

            verify(eventSignUpService).deleteById(eq(40L))
            verifyNoInteractions(jobs)
        }

        @Test
        fun `a guest removing their own sign up tells nobody`() {
            val signUp =
                emptySignUp().apply {
                    guest =
                        Guest.withRawToken(
                            name = "Guest",
                            discord = "guest#0001",
                            email = "guest-self@example.com",
                            accessToken = "MATCHING-TOKEN",
                        )
                }
            whenever(guestService.findByAccessToken("MATCHING-TOKEN")).thenReturn(signUp.guest!!)
            whenever(eventSignUpService.findById(37L)).thenReturn(signUp)

            useCases.delete(37L, "MATCHING-TOKEN", notify = true)

            verifyNoInteractions(jobs)
        }

        @Test
        fun `deletes sign up when guest token matches target signup`() {
            val signUp =
                emptySignUp().apply {
                    guest =
                        Guest.withRawToken(
                            name = "Guest",
                            discord = "guest#0001",
                            email = "guest-delete@example.com",
                            accessToken = "MATCHING-TOKEN",
                            phoneNumber = "0612345678",
                        )
                }
            whenever(guestService.findByAccessToken("MATCHING-TOKEN")).thenReturn(signUp.guest!!)
            whenever(eventSignUpService.findById(34L)).thenReturn(signUp)

            useCases.delete(34L, "MATCHING-TOKEN")

            verify(guestService).findByAccessToken("MATCHING-TOKEN")
            verify(eventSignUpService).findById(34L)
            verify(eventSignUpService).delete(signUp)
            verify(eventSignUpService, never()).deleteById(eq(34L))
        }

        @Test
        fun `a blank guest token is no token, so the sign-up is deleted by id`() {
            useCases.delete(41L, "   ")

            verify(eventSignUpService).deleteById(eq(41L))
        }

        @Test
        fun `a guest token against an account sign-up is refused`() {
            val signUp = EventSignUp(event = mock(), userId = 7L)
            whenever(guestService.findByAccessToken("SOME-TOKEN")).thenReturn(mock())
            whenever(eventSignUpService.findById(43L)).thenReturn(signUp)

            assertThatThrownBy { useCases.delete(43L, "SOME-TOKEN") }
                .isInstanceOf(ResponseStatusException::class.java)
                .extracting { (it as ResponseStatusException).statusCode }
                .isEqualTo(HttpStatus.FORBIDDEN)
        }

        @Test
        fun `rejects delete when guest token does not belong to target signup`() {
            val signUp =
                emptySignUp().apply {
                    guest =
                        Guest.withRawToken(
                            name = "Guest",
                            discord = "guest#0001",
                            email = "guest-mismatch@example.com",
                            accessToken = "REAL-TOKEN",
                            phoneNumber = "0612345678",
                        )
                }
            whenever(guestService.findByAccessToken("WRONG-TOKEN")).thenReturn(
                Guest.withRawToken(
                    name = "Other Guest",
                    discord = "guest#0002",
                    email = "guest-other@example.com",
                    accessToken = "WRONG-TOKEN",
                    phoneNumber = "0612345678",
                ),
            )
            whenever(eventSignUpService.findById(35L)).thenReturn(signUp)

            assertThatThrownBy {
                useCases.delete(35L, "WRONG-TOKEN")
            }.isInstanceOfSatisfying(ResponseStatusException::class.java) { ex ->
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
                ResponseStatusException(HttpStatus.NOT_FOUND, "Guest not found"),
            )

            assertThatThrownBy {
                useCases.delete(36L, "UNKNOWN-TOKEN")
            }.isInstanceOfSatisfying(ResponseStatusException::class.java) { ex ->
                assertThat(ex.statusCode).isEqualTo(HttpStatus.NOT_FOUND)
            }

            verify(guestService).findByAccessToken("UNKNOWN-TOKEN")
            verify(eventSignUpService, never()).findById(eq(36L))
            verify(eventSignUpService, never()).deleteById(eq(36L))
        }
    }

    private fun emptySignUp(): EventSignUp = EventSignUp(event = mock())
}

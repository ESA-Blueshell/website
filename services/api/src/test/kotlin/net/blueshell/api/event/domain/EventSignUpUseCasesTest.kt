package net.blueshell.api.event.domain

import jakarta.validation.ConstraintViolation
import jakarta.validation.ConstraintViolationException
import jakarta.validation.Validator
import net.blueshell.api.event.persistence.Event
import net.blueshell.api.event.persistence.EventRepository
import net.blueshell.api.event.persistence.EventSignUp
import net.blueshell.api.event.persistence.Guest
import net.blueshell.api.shared.enums.Role
import net.blueshell.api.shared.job.JobQueue
import net.blueshell.api.shared.job.JobTrigger
import net.blueshell.api.shared.security.CurrentUser
import net.blueshell.api.shared.security.CurrentUserProvider
import net.blueshell.api.survey.api.AnswerData
import net.blueshell.api.survey.api.QuestionService
import net.blueshell.api.testsupport.Entities
import net.blueshell.api.user.api.UserService
import net.blueshell.api.user.persistence.User
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.KArgumentCaptor
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.springframework.dao.OptimisticLockingFailureException
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException
import java.util.Optional

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
            whenever(currentUser.currentUser()).thenReturn(CurrentUser(id = 42L, roles = setOf(Role.MEMBER), addressId = null))
            val eventRef = Entities.event()
            val questionRef = Entities.question()
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
            assertThat(captured.firstValue.version).isEqualTo(0L)
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
            val eventRef = Entities.event()
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
            val eventRef = Entities.event()
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
    inner class AddForSomebodyElse {
        private fun event(
            eventId: Long = 100L,
            membersOnly: Boolean = false,
        ): KArgumentCaptor<EventSignUp> {
            val event = Entities.event(id = eventId, membersOnly = membersOnly)
            whenever(eventRepository.findById(eventId)).thenReturn(Optional.of(event))
            whenever(eventRepository.getReferenceById(eventId)).thenReturn(event)
            val stored = argumentCaptor<EventSignUp>()
            whenever(eventSignUpService.add(stored.capture())).thenAnswer { stored.firstValue.also { it.id = 77L } }
            return stored
        }

        @Test
        fun `a board member adds a sign-up for another account`() {
            event()
            whenever(users.findById(9L)).thenReturn(account(9L, Role.MEMBER))

            val added = useCases.create(EventSignUpData(eventId = 100L, userId = 9L), principalId = 1L)

            assertThat(added.userId).isEqualTo(9L)
            assertThat(added.guest).isNull()
        }

        @Test
        fun `a board member adds a sign-up for a guest`() {
            event()

            val added = useCases.create(EventSignUpData(eventId = 100L, guest = gordon), principalId = 1L)

            assertThat(added.userId).isNull()
            assertThat(added.guest?.name).isEqualTo("Guest Gordon")
        }

        @Test
        fun `the deadline and the limit do not bind a sign-up the board adds`() {
            event()
            whenever(users.findById(9L)).thenReturn(account(9L, Role.MEMBER))

            useCases.create(EventSignUpData(eventId = 100L, userId = 9L), principalId = 1L)

            val validated = argumentCaptor<EventSignUpData>()
            verify(validator).validate(validated.capture())
            assertThat(validated.firstValue.boardEdit).isTrue()
        }

        @Test
        fun `a sign-up the validator refuses is not added`() {
            event()
            whenever(users.findById(9L)).thenReturn(account(9L, Role.MEMBER))
            whenever(validator.validate(any<EventSignUpData>()))
                .thenReturn(setOf(mock<ConstraintViolation<EventSignUpData>>()))

            assertThatThrownBy { useCases.create(EventSignUpData(eventId = 100L, userId = 9L), principalId = 1L) }
                .isInstanceOf(ConstraintViolationException::class.java)

            verify(eventSignUpService, never()).add(any())
        }

        @Test
        fun `refuses an account that already has a sign-up for the event`() {
            event()
            whenever(users.findById(9L)).thenReturn(account(9L, Role.MEMBER))
            whenever(eventSignUpService.existsByUserIdAndEventId(9L, 100L)).thenReturn(true)

            assertThatThrownBy { useCases.create(EventSignUpData(eventId = 100L, userId = 9L), principalId = 1L) }
                .isInstanceOf(ResponseStatusException::class.java)
                .extracting { (it as ResponseStatusException).statusCode }
                .isEqualTo(HttpStatus.CONFLICT)
        }

        @Test
        fun `refuses a non-member account on a members-only event`() {
            event(membersOnly = true)
            whenever(users.findById(9L)).thenReturn(account(9L, Role.GUEST))

            assertThatThrownBy { useCases.create(EventSignUpData(eventId = 100L, userId = 9L), principalId = 1L) }
                .isInstanceOf(ResponseStatusException::class.java)
                .extracting { (it as ResponseStatusException).statusCode }
                .isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT)
        }

        @Test
        fun `refuses a guest on a members-only event`() {
            event(membersOnly = true)

            assertThatThrownBy { useCases.create(EventSignUpData(eventId = 100L, guest = gordon), principalId = 1L) }
                .isInstanceOf(ResponseStatusException::class.java)
                .extracting { (it as ResponseStatusException).statusCode }
                .isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT)
        }

        @Test
        fun `lets an inherited membership onto a members-only event`() {
            event(membersOnly = true)
            whenever(users.findById(9L)).thenReturn(account(9L, Role.BOARD))

            val added = useCases.create(EventSignUpData(eventId = 100L, userId = 9L), principalId = 1L)

            assertThat(added.userId).isEqualTo(9L)
        }

        @Test
        fun `refuses a body that names both an account and a guest`() {
            event()

            assertThatThrownBy {
                useCases.create(EventSignUpData(eventId = 100L, userId = 9L, guest = gordon), principalId = 1L)
            }.isInstanceOf(ResponseStatusException::class.java)
                .extracting { (it as ResponseStatusException).statusCode }
                .isEqualTo(HttpStatus.BAD_REQUEST)
        }

        @Test
        fun `an event that is not there is not found`() {
            whenever(eventRepository.findById(404L)).thenReturn(Optional.empty())

            assertThatThrownBy { useCases.create(EventSignUpData(eventId = 404L, userId = 9L), principalId = 1L) }
                .isInstanceOf(ResponseStatusException::class.java)
                .extracting { (it as ResponseStatusException).statusCode }
                .isEqualTo(HttpStatus.NOT_FOUND)
        }

        @Test
        fun `a board member who names their own account signs up themselves`() {
            val eventRef = Entities.event(id = 100L)
            whenever(eventRepository.getReferenceById(100L)).thenReturn(eventRef)
            val stored = argumentCaptor<EventSignUp>()
            whenever(eventSignUpService.create(stored.capture())).thenAnswer { stored.firstValue }

            val own = useCases.create(EventSignUpData(eventId = 100L, userId = 1L), principalId = 1L)

            assertThat(own.userId).isEqualTo(1L)
            val validated = argumentCaptor<EventSignUpData>()
            verify(validator).validate(validated.capture())
            assertThat(validated.firstValue.boardEdit).isFalse()
        }

        @Test
        fun `a member who names another account signs up themselves, bound by the deadline and the limit`() {
            whenever(currentUser.currentUser()).thenReturn(CurrentUser(id = 42L, roles = setOf(Role.MEMBER), addressId = null))
            val eventRef = Entities.event(id = 100L)
            whenever(eventRepository.getReferenceById(100L)).thenReturn(eventRef)
            val stored = argumentCaptor<EventSignUp>()
            whenever(eventSignUpService.create(stored.capture())).thenAnswer { stored.firstValue }

            val own = useCases.create(EventSignUpData(eventId = 100L, userId = 9L), principalId = 42L)

            assertThat(own.userId).isEqualTo(42L)
            val validated = argumentCaptor<EventSignUpData>()
            verify(validator).validate(validated.capture())
            assertThat(validated.firstValue.boardEdit).isFalse()
        }

        @Test
        fun `nobody is emailed unless the board asks for it`() {
            event()

            useCases.create(EventSignUpData(eventId = 100L, guest = gordon), principalId = 1L)

            verifyNoInteractions(jobs)
        }

        @Test
        fun `tells a guest the board added them when asked to, with their access link`() {
            event()

            useCases.create(EventSignUpData(eventId = 100L, guest = gordon), principalId = 1L, notify = true)

            verify(jobs).runAsync(
                eq(EventJobs.EventSignUpAdded),
                eq(EventJobs.EventSignupPayload(eventSignUpId = 77L, guestAccessToken = "GORDON-TOKEN")),
                eq(JobTrigger.SITE_ACTION),
                anyOrNull(),
            )
        }

        @Test
        fun `an account the board adds is not emailed, even asking for it`() {
            event()
            whenever(users.findById(9L)).thenReturn(account(9L, Role.MEMBER))

            useCases.create(EventSignUpData(eventId = 100L, userId = 9L), principalId = 1L, notify = true)

            verifyNoInteractions(jobs)
        }

        @Test
        fun `a guest signing up themselves gets the confirmation, not the board's email, even asking for it`() {
            whenever(currentUser.currentUser()).thenReturn(null)
            val eventRef = Entities.event(id = 100L)
            whenever(eventRepository.getReferenceById(100L)).thenReturn(eventRef)
            val stored = argumentCaptor<EventSignUp>()
            whenever(eventSignUpService.create(stored.capture())).thenAnswer { stored.firstValue }

            useCases.create(EventSignUpData(eventId = 100L, guest = gordon), principalId = null, notify = true)

            verifyNoInteractions(jobs)
        }
    }

    @Nested
    inner class UpdateEventSignUp {
        @Test
        fun `updates sign up resolved by principal when access token is missing`() {
            val existing = emptySignUp().apply { id = 11L }
            val eventRef = Entities.event()
            val questionRef = Entities.question()
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
                        version = 0L,
                    ),
                    42L,
                    null,
                )

            assertThat(existing.event).isSameAs(eventRef)
            assertThat(existing.userId).isEqualTo(42L)
            assertThat(existing.version).isEqualTo(0L)
            assertThat(existing.answers).hasSize(1)
            assertThat(existing.answers.first().question).isSameAs(questionRef)
            assertThat(existing.answers.first().textResponse).isEqualTo("Updated")
            assertThat(result).isSameAs(existing)
        }

        @Test
        fun `refuses an own edit made against an older version`() {
            val existing = emptySignUp().apply { version = 2L }
            whenever(eventSignUpService.findByUserIdAndEventId(42L, 100L)).thenReturn(existing)

            assertThatThrownBy {
                useCases.update(100L, EventSignUpData(eventId = 100L, answers = emptyList(), version = 1L), 42L, null)
            }.isInstanceOf(OptimisticLockingFailureException::class.java)
            verify(eventSignUpService, never()).update(any())
        }

        @Test
        fun `updates sign up resolved by guest access token`() {
            val existing = emptySignUp()
            val eventRef = Entities.event()
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
            val eventRef = Entities.event(id = 100L)
            whenever(eventRepository.getReferenceById(100L)).thenReturn(eventRef)
            val questionRef = Entities.question()
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
                        version = 0L,
                    ),
                )

            assertThat(result.answers).hasSize(1)
            assertThat(result.userId).isEqualTo(7L)
            verify(eventSignUpService).update(signUp)
        }

        @Test
        fun `refuses a board edit made against an older version, before looking at a move`() {
            val signUp = emptySignUp().apply { version = 2L }
            whenever(eventSignUpService.findById(40L)).thenReturn(signUp)

            assertThatThrownBy {
                useCases.updateById(40L, EventSignUpData(eventId = 0L, answers = emptyList(), userId = 9L, version = 1L))
            }.isInstanceOf(OptimisticLockingFailureException::class.java)
            verify(eventSignUpService, never()).update(any())
        }

        @Test
        fun `edits the guest's own details on a guest sign-up`() {
            val eventRef = Entities.event(id = 100L)
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
            val eventRef = Entities.event(id = 100L)
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
            val eventRef = Entities.event(id = 100L)
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
            val eventRef = Entities.event(id = 100L)
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
            val event = Entities.event(id = eventId, membersOnly = membersOnly)
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
                .isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT)
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
            val event = Entities.event(id = 100L)
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
            val event = Entities.event(id = 100L)
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
            val eventRef = Entities.event(id = 100L)
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
            val event = Entities.event(title = "LAN Party")
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
                eq(EventJobs.EventSignUpRemoved),
                eq(
                    EventJobs.EventSignUpRemovedPayload(
                        recipientEmail = "gordon@example.com",
                        recipientName = "Guest Gordon",
                        eventTitle = "LAN Party",
                    ),
                ),
                eq(JobTrigger.SITE_ACTION),
                anyOrNull(),
            )
        }

        @Test
        fun `tells an account holder at the address on their account`() {
            val event = Entities.event(title = "LAN Party")
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
                eq(EventJobs.EventSignUpRemoved),
                eq(
                    EventJobs.EventSignUpRemovedPayload(
                        recipientEmail = "ada@example.com",
                        recipientName = "Ada Lovelace",
                        eventTitle = "LAN Party",
                    ),
                ),
                eq(JobTrigger.SITE_ACTION),
                anyOrNull(),
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
            val event = Entities.event()
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
            val signUp = EventSignUp(event = Entities.event(), userId = 7L)
            whenever(guestService.findByAccessToken("SOME-TOKEN")).thenReturn(
                Guest.withRawToken(name = "Guest", discord = "guest#0003", email = "guest-some@example.com", accessToken = "SOME-TOKEN"),
            )
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

            verify(eventSignUpService, never()).deleteById(eq(36L))
        }
    }

    private val gordon =
        GuestData(
            name = "Guest Gordon",
            email = "gordon@example.com",
            discord = "gordon#0001",
            phoneNumber = "0611111111",
            accessToken = "GORDON-TOKEN",
        )

    private fun emptySignUp(): EventSignUp = EventSignUp(event = Entities.event())
}

package net.blueshell.api.event.domain

import net.blueshell.api.committee.api.CommitteeService
import net.blueshell.api.event.api.AnnounceChoice
import net.blueshell.api.event.api.AnnounceChoice.NEXT_MORNING
import net.blueshell.api.event.api.AnnounceChoice.NOW
import net.blueshell.api.event.api.AnnouncementLedger
import net.blueshell.api.event.api.EventService
import net.blueshell.api.event.persistence.Event
import net.blueshell.api.event.persistence.PingedRole
import net.blueshell.api.file.api.FileService
import net.blueshell.api.game.api.GameArchived
import net.blueshell.api.game.api.GameService
import net.blueshell.api.shared.enums.QuestionType
import net.blueshell.api.shared.enums.Role
import net.blueshell.api.shared.security.CurrentUser
import net.blueshell.api.shared.security.CurrentUserProvider
import net.blueshell.api.survey.api.QuestionData
import net.blueshell.api.survey.api.SurveyData
import net.blueshell.api.survey.api.SurveyFactory
import net.blueshell.api.testsupport.Entities
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.dao.OptimisticLockingFailureException
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class EventUseCasesTest {
    private val eventService = mock<EventService>()
    private val committeeService = mock<CommitteeService>()
    private val currentUserProvider = mock<CurrentUserProvider>()
    private val surveyFactory = mock<SurveyFactory>()
    private val fileService = mock<FileService>()
    private val games = mock<GameService>()
    private val announcements = mock<AnnouncementLedger>()
    private val approvals = mock<EventApprovals>()

    // Tuesday 6 January 2026, 14:00 in Amsterdam: the next 08:00 is Wednesday's.
    private val now = Instant.parse("2026-01-06T13:00:00Z")
    private val useCases =
        EventUseCases(
            eventService,
            committeeService,
            currentUserProvider,
            surveyFactory,
            fileService,
            games,
            announcements,
            approvals,
            clock = Clock.fixed(now, ZoneOffset.UTC),
        )

    @Nested
    inner class CreateEvent {
        @Test
        fun `creates event with mapped fields for board user`() {
            val committee = Entities.committee()
            val survey = Entities.survey()
            val bannerFile = Entities.file(id = 77L)
            whenever(committeeService.findById(3L)).thenReturn(committee)
            whenever(currentUserProvider.currentUser()).thenReturn(CurrentUser(1L, setOf(Role.BOARD), null))
            whenever(surveyFactory.createFromData(anySurveyData())).thenReturn(survey)
            whenever(fileService.findById(77L)).thenReturn(bannerFile)
            val captured = argumentCaptor<Event>()
            whenever(eventService.create(captured.capture())).thenAnswer { captured.firstValue }
            val data = createEventData(approved = true)

            val result = useCases.create(data, NOW)

            assertThat(captured.firstValue.committee).isSameAs(committee)
            assertThat(captured.firstValue.title).isEqualTo("Event title")
            assertThat(captured.firstValue.description).isEqualTo("Event description")
            assertThat(captured.firstValue.location).isEqualTo("Utrecht")
            assertThat(captured.firstValue.startTime).isEqualTo(data.startTime)
            assertThat(captured.firstValue.endTime).isEqualTo(data.endTime)
            assertThat(captured.firstValue.memberPrice).isEqualTo(10.0)
            assertThat(captured.firstValue.publicPrice).isEqualTo(20.0)
            assertThat(captured.firstValue.membersOnly).isTrue()
            assertThat(captured.firstValue.signUp).isTrue()
            assertThat(captured.firstValue.banner?.file).isSameAs(bannerFile)
            assertThat(captured.firstValue.signUpForm).isSameAs(survey)
            assertThat(captured.firstValue.approved).isTrue()
            assertThat(result).isSameAs(captured.firstValue)
        }

        @Test
        fun `forces event approval to false for non board user`() {
            val committee = Entities.committee()
            val bannerFile = Entities.file()
            whenever(committeeService.findById(3L)).thenReturn(committee)
            whenever(currentUserProvider.currentUser()).thenReturn(CurrentUser(2L, setOf(Role.MEMBER), null))
            whenever(fileService.findById(77L)).thenReturn(bannerFile)
            val captured = argumentCaptor<Event>()
            whenever(eventService.create(captured.capture())).thenAnswer { captured.firstValue }

            val result = useCases.create(createEventData(approved = true), NOW)

            assertThat(result.approved).isFalse()
        }
    }

    @Nested
    inner class UpdateEvent {
        @Test
        fun `updates event fields, keeping the version it was read at`() {
            val existing = eventEntity().apply { version = 1L }
            val committee = Entities.committee()
            val survey = Entities.survey()
            val bannerFile = Entities.file(id = 88L)
            whenever(eventService.findById(9L)).thenReturn(existing)
            whenever(committeeService.findById(4L)).thenReturn(committee)
            whenever(currentUserProvider.currentUser()).thenReturn(CurrentUser(1L, setOf(Role.BOARD), null))
            whenever(surveyFactory.createFromData(anySurveyData())).thenReturn(survey)
            whenever(fileService.findById(88L)).thenReturn(bannerFile)
            whenever(eventService.update(eq(existing), eq(false))).thenReturn(existing)
            val data = updateEventData()

            val result =
                useCases.update(
                    id = 9L,
                    data = data,
                    removeExistingSignUps = false,
                    version = 1L,
                    announce = NOW,
                )

            assertThat(existing.committee).isSameAs(committee)
            assertThat(existing.title).isEqualTo("Updated title")
            assertThat(existing.description).isEqualTo("Updated description")
            assertThat(existing.location).isEqualTo("Amsterdam")
            assertThat(existing.startTime).isEqualTo(data.startTime)
            assertThat(existing.endTime).isEqualTo(data.endTime)
            assertThat(existing.memberPrice).isEqualTo(12.0)
            assertThat(existing.publicPrice).isEqualTo(24.0)
            assertThat(existing.membersOnly).isFalse()
            assertThat(existing.signUp).isTrue()
            assertThat(existing.banner?.file).isSameAs(bannerFile)
            assertThat(existing.signUpForm).isSameAs(survey)
            assertThat(existing.approved).isTrue()
            assertThat(existing.version).isEqualTo(1L)
            assertThat(result).isSameAs(existing)
        }

        /** The event after [role]'s edit of one stored [approved], [awaiting] re-approval. */
        private fun editedBy(
            role: Role,
            approved: Boolean,
            awaiting: Boolean = false,
        ): Event {
            val existing =
                eventEntity().apply {
                    version = 1L
                    this.approved = approved
                    awaitingReapproval = awaiting
                }
            val bannerFile = Entities.file(id = 88L)
            whenever(eventService.findById(9L)).thenReturn(existing)
            whenever(committeeService.findById(4L)).thenReturn(Entities.committee(id = 4L))
            whenever(currentUserProvider.currentUser()).thenReturn(CurrentUser(2L, setOf(role), null))
            whenever(surveyFactory.createFromData(anySurveyData())).thenReturn(Entities.survey())
            whenever(fileService.findById(88L)).thenReturn(bannerFile)
            whenever(eventService.update(eq(existing), eq(false))).thenReturn(existing)
            return useCases.update(id = 9L, data = updateEventData(), removeExistingSignUps = false, version = 1L, announce = NOW)
        }

        @Test
        fun `sends an approved event back to the board on an edit by anybody else, and only then`() {
            val sentBack = editedBy(Role.COMMITTEE, approved = true)
            assertThat(sentBack.approved to sentBack.awaitingReapproval).isEqualTo(false to true)

            val stillWaiting = editedBy(Role.COMMITTEE, approved = false, awaiting = true)
            assertThat(stillWaiting.approved to stillWaiting.awaitingReapproval).isEqualTo(false to true)

            val neverApproved = editedBy(Role.COMMITTEE, approved = false)
            assertThat(neverApproved.approved to neverApproved.awaitingReapproval).isEqualTo(false to false)

            val byTheBoard = editedBy(Role.BOARD, approved = true)
            assertThat(byTheBoard.approved to byTheBoard.awaitingReapproval).isEqualTo(true to false)
        }

        @Test
        fun `refuses an edit made against an older version, before touching a field`() {
            val existing = eventEntity().apply { version = 2L }
            whenever(eventService.findById(9L)).thenReturn(existing)

            assertThatThrownBy { useCases.update(id = 9L, data = updateEventData(), removeExistingSignUps = false, version = 1L) }
                .isInstanceOf(OptimisticLockingFailureException::class.java)
            assertThat(existing.title).isNotEqualTo("Updated title")
            verify(eventService, never()).update(any(), any())
        }
    }

    @Nested
    inner class PingedRoles {
        private fun asBoard() {
            whenever(currentUserProvider.currentUser()).thenReturn(CurrentUser(1L, setOf(Role.BOARD), null))
            whenever(fileService.findById(any())).thenReturn(Entities.file())
            whenever(surveyFactory.createFromData(anySurveyData())).thenReturn(Entities.survey())
        }

        @Test
        fun `keeps the roles an event pings, with each one's name`() {
            asBoard()
            whenever(committeeService.findById(3L)).thenReturn(Entities.committee(id = 3L))
            val captured = argumentCaptor<Event>()
            whenever(eventService.create(captured.capture())).thenAnswer { captured.firstValue }

            useCases.create(createEventData(approved = true).copy(pingedRoles = listOf(PingedRoleData("901", "Gamers"))), NOW)

            assertThat(captured.firstValue.pingedRoles).containsExactly(PingedRole("901", "Gamers"))
        }

        @Test
        fun `refuses @everyone as a pinged role, on a new event and on an edit`() {
            asBoard()
            val guarded =
                EventUseCases(
                    eventService,
                    committeeService,
                    currentUserProvider,
                    surveyFactory,
                    fileService,
                    games,
                    announcements,
                    approvals,
                    discordGuildId = "324",
                )
            val everyone = listOf(PingedRoleData("324", "@everyone"))
            whenever(eventService.findById(9L)).thenReturn(eventEntity().apply { version = 1L })

            assertThatThrownBy { guarded.create(createEventData(approved = true).copy(pingedRoles = everyone), NOW) }
                .isInstanceOf(InvalidEventException::class.java)
            assertThatThrownBy {
                guarded.update(id = 9L, data = updateEventData().copy(pingedRoles = everyone), removeExistingSignUps = false, version = 1L)
            }.isInstanceOf(InvalidEventException::class.java)
        }

        @Test
        fun `leaves the roles alone when an edit says nothing of them, and replaces them when it does`() {
            asBoard()
            val existing = eventEntity().apply { pingedRoles += PingedRole("901", "Gamers") }
            whenever(eventService.findById(9L)).thenReturn(existing)
            whenever(committeeService.findById(4L)).thenReturn(Entities.committee(id = 4L))
            whenever(eventService.update(eq(existing), eq(false))).thenReturn(existing)

            useCases.update(id = 9L, data = updateEventData(), removeExistingSignUps = false, version = 0L, announce = NOW)
            assertThat(existing.pingedRoles).containsExactly(PingedRole("901", "Gamers"))

            useCases.update(
                id = 9L,
                data = updateEventData().copy(pingedRoles = listOf(PingedRoleData("902", "Board"))),
                removeExistingSignUps = false,
                version = 0L,
                announce = NOW,
            )
            assertThat(existing.pingedRoles).containsExactly(PingedRole("902", "Board"))
        }
    }

    @Nested
    inner class Games {
        private fun asBoard() {
            whenever(currentUserProvider.currentUser()).thenReturn(CurrentUser(1L, setOf(Role.BOARD), null))
            whenever(fileService.findById(any())).thenReturn(Entities.file())
            whenever(surveyFactory.createFromData(anySurveyData())).thenReturn(Entities.survey())
            whenever(committeeService.findById(any())).thenReturn(Entities.committee())
        }

        @Test
        fun `names the games a new event picks, as the game module answers them`() {
            asBoard()
            whenever(games.requireNameable(listOf(" CHESS ", "WORDLE"), emptySet())).thenReturn(listOf("CHESS", "WORDLE"))
            val captured = argumentCaptor<Event>()
            whenever(eventService.create(captured.capture())).thenAnswer { captured.firstValue }

            useCases.create(createEventData(approved = true).copy(gameCodes = listOf(" CHESS ", "WORDLE")), NOW)

            assertThat(captured.firstValue.gameCodes).containsExactly("CHESS", "WORDLE")
        }

        @Test
        fun `keeps the games an edit says nothing of, and lets an archived one stay named`() {
            asBoard()
            val existing = eventEntity().apply { gameCodes += "DOTA_2" }
            whenever(eventService.findById(9L)).thenReturn(existing)
            whenever(eventService.update(eq(existing), eq(false))).thenReturn(existing)
            whenever(games.requireNameable(listOf("DOTA_2", "CHESS"), setOf("DOTA_2"))).thenReturn(listOf("DOTA_2", "CHESS"))

            useCases.update(id = 9L, data = updateEventData(), removeExistingSignUps = false, version = 0L, announce = NOW)
            assertThat(existing.gameCodes).containsExactly("DOTA_2")

            val both = updateEventData().copy(gameCodes = listOf("DOTA_2", "CHESS"))
            useCases.update(id = 9L, data = both, removeExistingSignUps = false, version = 0L, announce = NOW)
            assertThat(existing.gameCodes).containsExactly("DOTA_2", "CHESS")
        }

        @Test
        fun `saves nothing when a game is refused`() {
            asBoard()
            whenever(games.requireNameable(listOf("DOTA_2"), emptySet())).thenThrow(GameArchived("Dota 2"))

            assertThatThrownBy { useCases.create(createEventData(approved = true).copy(gameCodes = listOf("DOTA_2")), NOW) }
                .isInstanceOf(GameArchived::class.java)
            verify(eventService, never()).create(any())
        }
    }

    @Nested
    inner class ApproveEvent {
        @Test
        fun `updates approval status of event`() {
            val existing = eventEntity().apply { approved = false }
            whenever(eventService.findById(6L)).thenReturn(existing)
            whenever(eventService.update(existing)).thenReturn(existing)

            val result = useCases.approve(id = 6L, approved = true, announce = NOW)

            assertThat(existing.approved).isTrue()
            assertThat(result).isSameAs(existing)
        }

        @Test
        fun `settles an event awaiting re-approval, whichever way the board decides`() {
            val approved = eventEntity().apply { awaitingReapproval = true }
            val declined = eventEntity().apply { awaitingReapproval = true }
            whenever(eventService.findById(6L)).thenReturn(approved)
            whenever(eventService.findById(7L)).thenReturn(declined)

            useCases.approve(id = 6L, approved = true, announce = NOW)
            useCases.approve(id = 7L, approved = false)

            assertThat(approved.approved to approved.awaitingReapproval).isEqualTo(true to false)
            assertThat(declined.approved to declined.awaitingReapproval).isEqualTo(false to false)
        }

        @Test
        fun `keeps what the board approves, for a later re-approval to be read against, and nothing it declines`() {
            val approved = eventEntity().apply { id = 6L }
            val declined = eventEntity().apply { id = 7L }
            whenever(eventService.findById(6L)).thenReturn(approved)
            whenever(eventService.findById(7L)).thenReturn(declined)

            useCases.approve(id = 6L, approved = true, announce = NOW)
            useCases.approve(id = 7L, approved = false)

            verify(approvals).record(approved)
            verify(approvals, never()).record(declined)
        }
    }

    @Nested
    inner class Announcing {
        private fun approving(
            announce: AnnounceChoice?,
            postOut: Boolean = false,
        ): Event {
            val existing = eventEntity().apply { id = 6L }
            whenever(eventService.findById(6L)).thenReturn(existing)
            whenever(announcements.announced(6L)).thenReturn(postOut)
            useCases.approve(id = 6L, approved = true, announce = announce)
            return existing
        }

        @Test
        fun `sends the events-info post now, or at the next 08 00 Amsterdam time, as the board chose`() {
            assertThat(approving(NOW).announceAt).isEqualTo(now)
            assertThat(approving(NEXT_MORNING).announceAt).isEqualTo(Instant.parse("2026-01-07T07:00:00Z"))
        }

        @Test
        fun `refuses an approval that does not say when the post goes out, while it is not out`() {
            assertThatThrownBy { approving(announce = null) }.isInstanceOf(InvalidEventException::class.java)
            verify(eventService, never()).update(any())
        }

        @Test
        fun `asks nothing once the post is out, and leaves its time alone`() {
            assertThat(approving(announce = null, postOut = true).announceAt).isNull()
        }

        @Test
        fun `asks a new event the board approves on creating it`() {
            whenever(currentUserProvider.currentUser()).thenReturn(CurrentUser(1L, setOf(Role.BOARD), null))
            whenever(committeeService.findById(3L)).thenReturn(Entities.committee(id = 3L))
            whenever(fileService.findById(77L)).thenReturn(Entities.file(id = 77L))
            whenever(surveyFactory.createFromData(anySurveyData())).thenReturn(Entities.survey())
            val created = argumentCaptor<Event>()
            whenever(eventService.create(created.capture())).thenAnswer { created.firstValue }

            assertThatThrownBy { useCases.create(createEventData(approved = true)) }.isInstanceOf(InvalidEventException::class.java)
            useCases.create(createEventData(approved = true), NEXT_MORNING)

            assertThat(created.firstValue.announceAt).isEqualTo(Instant.parse("2026-01-07T07:00:00Z"))
        }

        @Test
        fun `forgets the time on unapproving, so approving again asks again`() {
            val approved =
                eventEntity().apply {
                    id = 7L
                    this.approved = true
                    announceAt = now
                }
            whenever(eventService.findById(7L)).thenReturn(approved)

            useCases.approve(id = 7L, approved = false)
            assertThat(approved.announceAt).isNull()
            assertThatThrownBy { useCases.approve(id = 7L, approved = true) }.isInstanceOf(InvalidEventException::class.java)
        }

        @Test
        fun `keeps the time of an event already approved through an edit`() {
            val approved =
                eventEntity().apply {
                    id = 9L
                    version = 1L
                    this.approved = true
                    announceAt = now
                }
            whenever(eventService.findById(9L)).thenReturn(approved)
            whenever(committeeService.findById(4L)).thenReturn(Entities.committee(id = 4L))
            whenever(currentUserProvider.currentUser()).thenReturn(CurrentUser(1L, setOf(Role.BOARD), null))
            whenever(surveyFactory.createFromData(anySurveyData())).thenReturn(Entities.survey())
            whenever(fileService.findById(88L)).thenReturn(Entities.file(id = 88L))
            whenever(eventService.update(eq(approved), eq(false))).thenReturn(approved)

            useCases.update(id = 9L, data = updateEventData(), removeExistingSignUps = false, version = 1L)

            assertThat(approved.announceAt).isEqualTo(now)
        }
    }

    @Nested
    inner class FindEventById {
        @Test
        fun `returns event by id`() {
            val expected = eventEntity()
            whenever(eventService.findById(12L)).thenReturn(expected)

            val result = eventService.findById(12L)

            assertThat(result).isSameAs(expected)
            verify(eventService).findById(12L)
        }
    }

    private fun createEventData(approved: Boolean): EventData =
        EventData(
            committeeId = 3L,
            title = "Event title",
            description = "Event description",
            location = "Utrecht",
            startTime = Instant.parse("2026-01-10T12:00:00Z"),
            endTime = Instant.parse("2026-01-10T14:00:00Z"),
            memberPrice = 10.0,
            publicPrice = 20.0,
            approved = approved,
            membersOnly = true,
            signUp = true,
            banner = EventBannerData(fileId = 77L),
            signUpForm = surveyData(),
        )

    private fun updateEventData(): EventData =
        EventData(
            committeeId = 4L,
            title = "Updated title",
            description = "Updated description",
            location = "Amsterdam",
            startTime = Instant.parse("2026-02-10T12:00:00Z"),
            endTime = Instant.parse("2026-02-10T14:00:00Z"),
            memberPrice = 12.0,
            publicPrice = 24.0,
            approved = true,
            membersOnly = false,
            signUp = true,
            banner = EventBannerData(fileId = 88L),
            signUpForm = surveyData(),
        )

    private fun surveyData(): SurveyData =
        SurveyData(
            questions =
                listOf(
                    QuestionData(
                        idx = 0L,
                        type = QuestionType.OPEN,
                        label = "Any allergies?",
                        choiceLabels = null,
                    ),
                ),
        )

    private fun anySurveyData(): SurveyData = surveyData()

    private fun eventEntity(): Event =
        Event(
            committee = mock(),
            title = "Event",
            startTime = Instant.parse("2026-01-01T10:00:00Z"),
            endTime = Instant.parse("2026-01-01T12:00:00Z"),
        )
}

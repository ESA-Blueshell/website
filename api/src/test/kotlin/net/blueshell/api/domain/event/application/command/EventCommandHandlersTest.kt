package net.blueshell.api.domain.event.application.command

import net.blueshell.api.domain.committee.application.CommitteeService
import net.blueshell.api.domain.committee.persistence.Committee
import net.blueshell.api.domain.event.application.EventService
import net.blueshell.api.domain.event.application.query.EventQuery
import net.blueshell.api.domain.event.command.ApproveEventCommand
import net.blueshell.api.domain.event.command.CreateEventCommand
import net.blueshell.api.domain.event.command.DeleteEventByIdCommand
import net.blueshell.api.domain.event.command.EventBannerData
import net.blueshell.api.domain.event.command.FindEventByIdCommand
import net.blueshell.api.domain.event.command.FindEventsCommand
import net.blueshell.api.domain.event.command.UpdateEventCommand
import net.blueshell.api.domain.event.persistence.Event
import net.blueshell.api.domain.survey.application.factory.SurveyFactory
import net.blueshell.api.domain.survey.command.QuestionData
import net.blueshell.api.domain.survey.command.SurveyData
import net.blueshell.api.domain.survey.persistence.Survey
import net.blueshell.api.shared.enums.QuestionType
import net.blueshell.api.shared.enums.Role
import net.blueshell.api.shared.security.CurrentUser
import net.blueshell.api.shared.security.CurrentUserProvider
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import java.time.Instant

class EventCommandHandlersTest {

    private val eventService = mock<EventService>()
    private val committeeService = mock<CommitteeService>()
    private val currentUserProvider = mock<CurrentUserProvider>()
    private val surveyFactory = mock<SurveyFactory>()

    @Nested
    inner class CreateEvent {

        private val handler = CreateEventHandler(eventService, committeeService, currentUserProvider, surveyFactory)

        @Test
        fun `creates event with mapped fields for board user`() {
            val committee = mock<Committee>()
            val survey = mock<Survey>()
            whenever(committeeService.findById(3L)).thenReturn(committee)
            whenever(currentUserProvider.currentUser()).thenReturn(CurrentUser(1L, setOf(Role.BOARD), null))
            whenever(surveyFactory.createFromData(anySurveyData())).thenReturn(survey)
            val captured = argumentCaptor<Event>()
            whenever(eventService.create(captured.capture())).thenAnswer { captured.firstValue }
            val command = createEventCommand(approved = true)

            val result = handler.handle(command)

            assertThat(captured.firstValue.committee).isSameAs(committee)
            assertThat(captured.firstValue.title).isEqualTo("Event title")
            assertThat(captured.firstValue.description).isEqualTo("Event description")
            assertThat(captured.firstValue.location).isEqualTo("Utrecht")
            assertThat(captured.firstValue.startTime).isEqualTo(command.startTime)
            assertThat(captured.firstValue.endTime).isEqualTo(command.endTime)
            assertThat(captured.firstValue.memberPrice).isEqualTo(10.0)
            assertThat(captured.firstValue.publicPrice).isEqualTo(20.0)
            assertThat(captured.firstValue.membersOnly).isTrue()
            assertThat(captured.firstValue.signUp).isTrue()
            assertThat(captured.firstValue.banner?.fileId).isEqualTo(77L)
            assertThat(captured.firstValue.signUpForm).isSameAs(survey)
            assertThat(captured.firstValue.approved).isTrue()
            assertThat(result).isSameAs(captured.firstValue)
        }

        @Test
        fun `forces event approval to false for non board user`() {
            val committee = mock<Committee>()
            whenever(committeeService.findById(3L)).thenReturn(committee)
            whenever(currentUserProvider.currentUser()).thenReturn(CurrentUser(2L, setOf(Role.MEMBER), null))
            val captured = argumentCaptor<Event>()
            whenever(eventService.create(captured.capture())).thenAnswer { captured.firstValue }

            val result = handler.handle(createEventCommand(approved = true))

            assertThat(result.approved).isFalse()
        }
    }

    @Nested
    inner class UpdateEvent {

        private val handler = UpdateEventHandler(eventService, committeeService, currentUserProvider, surveyFactory)

        @Test
        fun `updates event fields and version`() {
            val existing = Event().apply { version = 1L }
            val committee = mock<Committee>()
            val survey = mock<Survey>()
            whenever(eventService.findById(9L)).thenReturn(existing)
            whenever(committeeService.findById(4L)).thenReturn(committee)
            whenever(currentUserProvider.currentUser()).thenReturn(CurrentUser(1L, setOf(Role.BOARD), null))
            whenever(surveyFactory.createFromData(anySurveyData())).thenReturn(survey)
            whenever(eventService.update(existing)).thenReturn(existing)
            val command = updateEventCommand()

            val result = handler.handle(command)

            assertThat(existing.committee).isSameAs(committee)
            assertThat(existing.title).isEqualTo("Updated title")
            assertThat(existing.description).isEqualTo("Updated description")
            assertThat(existing.location).isEqualTo("Amsterdam")
            assertThat(existing.startTime).isEqualTo(command.startTime)
            assertThat(existing.endTime).isEqualTo(command.endTime)
            assertThat(existing.memberPrice).isEqualTo(12.0)
            assertThat(existing.publicPrice).isEqualTo(24.0)
            assertThat(existing.membersOnly).isFalse()
            assertThat(existing.signUp).isTrue()
            assertThat(existing.banner?.fileId).isEqualTo(88L)
            assertThat(existing.signUpForm).isSameAs(survey)
            assertThat(existing.approved).isTrue()
            assertThat(existing.version).isEqualTo(5L)
            assertThat(result).isSameAs(existing)
        }
    }

    @Nested
    inner class ApproveEvent {

        private val handler = ApproveEventHandler(eventService)

        @Test
        fun `updates approval status of event`() {
            val existing = Event().apply { approved = false }
            whenever(eventService.findById(6L)).thenReturn(existing)
            whenever(eventService.update(existing)).thenReturn(existing)

            val result = handler.handle(ApproveEventCommand(id = 6L, approved = true))

            assertThat(existing.approved).isTrue()
            assertThat(result).isSameAs(existing)
        }
    }

    @Nested
    inner class FindEventById {

        private val handler = FindEventByIdHandler(eventService)

        @Test
        fun `returns event by id`() {
            val expected = Event()
            whenever(eventService.findById(12L)).thenReturn(expected)

            val result = handler.handle(FindEventByIdCommand(12L))

            assertThat(result).isSameAs(expected)
            verify(eventService).findById(12L)
        }
    }

    @Nested
    inner class FindEvents {

        private val handler = FindEventsHandler(eventService)

        @Test
        fun `returns events page using filter and pageable`() {
            val pageable = PageRequest.of(0, 10)
            val filter = EventQuery(titleContains = "party")
            val page = PageImpl(listOf(Event()), pageable, 1)
            whenever(eventService.findByFilter(pageable, filter)).thenReturn(page)

            val result = handler.handle(FindEventsCommand(pageable = pageable, filter = filter))

            assertThat(result).isSameAs(page)
            verify(eventService).findByFilter(pageable, filter)
        }
    }

    @Nested
    inner class DeleteEventById {

        private val handler = DeleteEventByIdHandler(eventService)

        @Test
        fun `deletes event by id`() {
            handler.handle(DeleteEventByIdCommand(eventId = 18L))

            verify(eventService).deleteById(eq(18L))
        }
    }

    private fun createEventCommand(approved: Boolean): CreateEventCommand = CreateEventCommand(
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
        signUpForm = surveyData()
    )

    private fun updateEventCommand(): UpdateEventCommand = UpdateEventCommand(
        id = 9L,
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
        version = 5L
    )

    private fun surveyData(): SurveyData = SurveyData(
        questions = listOf(
            QuestionData(
                idx = 0L,
                type = QuestionType.OPEN,
                label = "Any allergies?",
                choiceLabels = null
            )
        )
    )

    private fun anySurveyData(): SurveyData = surveyData()
}

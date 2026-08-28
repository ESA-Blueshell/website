package net.blueshell.api.domain.event.application

import net.blueshell.api.committee.api.CommitteeService
import net.blueshell.api.committee.persistence.Committee
import net.blueshell.api.domain.event.application.query.EventQuery
import net.blueshell.api.domain.event.persistence.Event
import net.blueshell.api.file.api.FileService
import net.blueshell.api.file.persistence.File
import net.blueshell.api.domain.survey.application.factory.SurveyFactory
import net.blueshell.api.domain.survey.application.QuestionData
import net.blueshell.api.domain.survey.application.SurveyData
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

class EventUseCasesTest {

    private val eventService = mock<EventService>()
    private val committeeService = mock<CommitteeService>()
    private val currentUserProvider = mock<CurrentUserProvider>()
    private val surveyFactory = mock<SurveyFactory>()
    private val fileService = mock<FileService>()
    private val useCases = EventUseCases(eventService, committeeService, currentUserProvider, surveyFactory, fileService)

    @Nested
    inner class CreateEvent {


        @Test
        fun `creates event with mapped fields for board user`() {
            val committee = mock<Committee>()
            val survey = mock<Survey>()
            val bannerFile = mock<File>()
            whenever(bannerFile.id).thenReturn(77L)
            whenever(committeeService.findById(3L)).thenReturn(committee)
            whenever(currentUserProvider.currentUser()).thenReturn(CurrentUser(1L, setOf(Role.BOARD), null))
            whenever(surveyFactory.createFromData(anySurveyData())).thenReturn(survey)
            whenever(fileService.findById(77L)).thenReturn(bannerFile)
            val captured = argumentCaptor<Event>()
            whenever(eventService.create(captured.capture())).thenAnswer { captured.firstValue }
            val data = createEventData(approved = true)

            val result = useCases.create(data)

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
            val committee = mock<Committee>()
            val bannerFile = mock<File>()
            whenever(committeeService.findById(3L)).thenReturn(committee)
            whenever(currentUserProvider.currentUser()).thenReturn(CurrentUser(2L, setOf(Role.MEMBER), null))
            whenever(fileService.findById(77L)).thenReturn(bannerFile)
            val captured = argumentCaptor<Event>()
            whenever(eventService.create(captured.capture())).thenAnswer { captured.firstValue }

            val result = useCases.create(createEventData(approved = true))

            assertThat(result.approved).isFalse()
        }
    }

    @Nested
    inner class UpdateEvent {


        @Test
        fun `updates event fields and version`() {
            val existing = eventEntity().apply { version = 1L }
            val committee = mock<Committee>()
            val survey = mock<Survey>()
            val bannerFile = mock<File>()
            whenever(bannerFile.id).thenReturn(88L)
            whenever(eventService.findById(9L)).thenReturn(existing)
            whenever(committeeService.findById(4L)).thenReturn(committee)
            whenever(currentUserProvider.currentUser()).thenReturn(CurrentUser(1L, setOf(Role.BOARD), null))
            whenever(surveyFactory.createFromData(anySurveyData())).thenReturn(survey)
            whenever(fileService.findById(88L)).thenReturn(bannerFile)
            whenever(eventService.update(eq(existing), eq(false))).thenReturn(existing)
            val data = updateEventData()

            val result = useCases.update(
                id = 9L,
                data = data,
                removeExistingSignUps = false,
                version = 5L,
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
            assertThat(existing.version).isEqualTo(5L)
            assertThat(result).isSameAs(existing)
        }
    }

    @Nested
    inner class ApproveEvent {


        @Test
        fun `updates approval status of event`() {
            val existing = eventEntity().apply { approved = false }
            whenever(eventService.findById(6L)).thenReturn(existing)
            whenever(eventService.update(existing)).thenReturn(existing)

            val result = useCases.approve(id = 6L, approved = true)

            assertThat(existing.approved).isTrue()
            assertThat(result).isSameAs(existing)
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



    private fun createEventData(approved: Boolean): EventData = EventData(
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

    private fun updateEventData(): EventData = EventData(
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
        signUpForm = surveyData()
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

    private fun eventEntity(): Event = Event(
        committee = mock(),
        title = "Event",
        startTime = Instant.parse("2026-01-01T10:00:00Z"),
        endTime = Instant.parse("2026-01-01T12:00:00Z"),
    )
}

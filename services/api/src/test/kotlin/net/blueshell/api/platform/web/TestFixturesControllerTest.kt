package net.blueshell.api.platform.web

import net.blueshell.api.committee.api.CommitteePage
import net.blueshell.api.committee.api.CommitteeService
import net.blueshell.api.committee.domain.CommitteeMemberData
import net.blueshell.api.committee.persistence.Committee
import net.blueshell.api.committee.persistence.CommitteeMember
import net.blueshell.api.contribution.api.ContributionPeriodService
import net.blueshell.api.contribution.domain.ContributionUseCases
import net.blueshell.api.contribution.persistence.ContributionPeriod
import net.blueshell.api.event.api.EventService
import net.blueshell.api.event.persistence.Event
import net.blueshell.api.user.api.UserService
import net.blueshell.api.user.persistence.User
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Instant
import java.time.LocalDate

class TestFixturesControllerTest {
    private val committees = mock<CommitteeService>()
    private val periods = mock<ContributionPeriodService>()
    private val contributions = mock<ContributionUseCases>()
    private val events = mock<EventService>()
    private val users = mock<UserService>()
    private val controller = TestFixturesController(committees, periods, contributions, events, users)

    private fun user(id: Long) =
        User(
            username = "user$id",
            email = "user$id@example.com",
            password = "x",
            initials = "U",
            firstName = "U",
            lastName = "Ser",
        ).also { it.id = id }

    private fun committee(id: Long) = Committee(name = "Sitecie", description = "Site", listed = false).also { it.id = id }

    private val start = LocalDate.of(2026, 9, 1)
    private val end = LocalDate.of(2027, 8, 31)

    private fun periodFixture() = TestFixturesController.PeriodFixture(start, end, LocalDate.of(2027, 2, 1), fullYearFee = 40.0)

    @Test
    fun `a committee is created through its service`() {
        whenever(committees.createWithMembers("Sitecie", "Site", emptyList())).thenReturn(committee(4))

        assertThat(controller.committee(TestFixturesController.CommitteeFixture("Sitecie", "Site"))).isEqualTo(4)
    }

    @Test
    fun `seating a member keeps the members and the page the committee has`() {
        val committee = committee(4)
        committee.replaceMembers(listOf(CommitteeMember(committee = committee, user = user(1), role = "Chair")))
        whenever(committees.findById(4)).thenReturn(committee)
        whenever(users.findByUsername("user2")).thenReturn(user(2))

        controller.seat(4, TestFixturesController.CommitteeSeatFixture("user2", "Member"))

        val seats = argumentCaptor<List<CommitteeMemberData>>()
        verify(committees).updateWithMembers(
            eq(4L),
            eq("Sitecie"),
            eq("Site"),
            seats.capture(),
            isNull(),
            eq(CommitteePage(address = "sitecie", listed = false)),
        )
        assertThat(seats.firstValue).containsExactly(CommitteeMemberData(1, "Chair"), CommitteeMemberData(2, "Member"))
    }

    @Test
    fun `a period with the same dates is answered rather than created again`() {
        val existing = ContributionPeriod(start, end, LocalDate.of(2027, 2, 1)).also { it.id = 9 }
        whenever(periods.findAll()).thenReturn(listOf(existing))

        assertThat(controller.period(periodFixture())).isEqualTo(9)
        verify(periods, never()).create(any())
    }

    @Test
    fun `a new period is created with the fees asked for`() {
        whenever(periods.findAll()).thenReturn(emptyList())
        whenever(periods.create(any())).thenAnswer { (it.getArgument<ContributionPeriod>(0)).also { period -> period.id = 10 } }

        assertThat(controller.period(periodFixture())).isEqualTo(10)

        val created = argumentCaptor<ContributionPeriod>()
        verify(periods).create(created.capture())
        assertThat(created.firstValue.fullYearFee).isEqualTo(40.0)
    }

    @Test
    fun `a contribution is recorded for the named user`() {
        whenever(users.findByUsername("user3")).thenReturn(user(3))

        controller.contribution(9, TestFixturesController.ContributionFixture("user3"))

        verify(contributions).create(3, 9)
    }

    @Test
    fun `an event is created under its committee through its service`() {
        whenever(committees.findById(4)).thenReturn(committee(4))
        whenever(events.create(any())).thenAnswer { (it.getArgument<Event>(0)).also { event -> event.id = 12 } }
        val fixture =
            TestFixturesController.EventFixture(
                committeeId = 4,
                title = "LAN",
                startTime = Instant.parse("2026-10-01T18:00:00Z"),
                endTime = Instant.parse("2026-10-01T23:00:00Z"),
                approved = true,
                signUpLimit = 20,
            )

        assertThat(controller.event(fixture)).isEqualTo(12)

        val created = argumentCaptor<Event>()
        verify(events).create(created.capture())
        assertThat(created.firstValue.committee?.id).isEqualTo(4)
        assertThat(created.firstValue.approved).isTrue()
        assertThat(created.firstValue.signUpLimit).isEqualTo(20)
    }
}

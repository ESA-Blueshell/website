package net.blueshell.api.platform.integration.cohort.application

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.blueshell.api.domain.committee.application.CommitteeMemberService
import net.blueshell.api.domain.committee.application.CommitteeMembershipWindow
import net.blueshell.api.domain.committee.persistence.Committee
import net.blueshell.api.domain.committee.persistence.CommitteeMember
import net.blueshell.api.domain.contribution.application.ContributionPeriodService
import net.blueshell.api.domain.contribution.persistence.Contribution
import net.blueshell.api.domain.contribution.persistence.ContributionPeriod
import net.blueshell.api.domain.user.application.UserService
import net.blueshell.api.domain.user.persistence.Membership
import net.blueshell.api.domain.user.persistence.User
import net.blueshell.api.platform.integration.cohort.persistence.CohortFactKind
import net.blueshell.api.shared.enums.Role
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate

class UserFactCollectorTest {

    private val users: UserService = mockk()
    private val periods: ContributionPeriodService = mockk(relaxed = true)
    private val committeeMembers: CommitteeMemberService = mockk(relaxed = true)
    private val collector = UserFactCollector(users, periods, committeeMembers)

    @Test
    fun `returns empty set when user is not found`() {
        every { users.findById(999L) } throws RuntimeException("not found")

        assertThat(collector.collect(999L)).isEmpty()
    }

    @Test
    fun `emits one ROLE fact per role on the user`() {
        every { users.findById(1L) } returns userWith(
            roles = setOf(Role.GUEST, Role.MEMBER, Role.BOARD),
        )

        val facts = collector.collect(1L)

        assertThat(facts).contains(
            UserFact(CohortFactKind.ROLE, Role.GUEST.name),
            UserFact(CohortFactKind.ROLE, Role.MEMBER.name),
            UserFact(CohortFactKind.ROLE, Role.BOARD.name),
        )
    }

    @Test
    fun `emits one COMMITTEE fact per committee membership`() {
        every { users.findById(1L) } returns userWith(
            committeeMembers = setOf(
                committeeMembership(committeeId = 7L),
                committeeMembership(committeeId = 42L),
            ),
        )

        val facts = collector.collect(1L)

        assertThat(facts).contains(
            UserFact(CohortFactKind.COMMITTEE, "7"),
            UserFact(CohortFactKind.COMMITTEE, "42"),
        )
    }

    @Test
    fun `emits one CONTRIBUTION_PAID fact per contribution period`() {
        every { users.findById(1L) } returns userWith(
            contributions = setOf(
                contribution(periodId = 100L),
                contribution(periodId = 101L),
            ),
        )

        val facts = collector.collect(1L)

        assertThat(facts).contains(
            UserFact(CohortFactKind.CONTRIBUTION_PAID, "100"),
            UserFact(CohortFactKind.CONTRIBUTION_PAID, "101"),
        )
    }

    @Test
    fun `emits NEWSLETTER fact only when user has opted in`() {
        every { users.findById(1L) } returns userWith(newsletter = true)
        every { users.findById(2L) } returns userWith(newsletter = false)

        assertThat(collector.collect(1L))
            .contains(UserFact(CohortFactKind.NEWSLETTER, "true"))
        assertThat(collector.collect(2L))
            .noneMatch { it.kind == CohortFactKind.NEWSLETTER }
    }

    @Test
    fun `committee membership with null committee id is silently skipped`() {
        every { users.findById(1L) } returns userWith(
            committeeMembers = setOf(committeeMembership(committeeId = null)),
        )

        assertThat(collector.collect(1L))
            .noneMatch { it.kind == CohortFactKind.COMMITTEE }
    }

    private fun userWith(
        roles: Set<Role> = emptySet(),
        committeeMembers: Set<CommitteeMember> = emptySet(),
        contributions: Set<Contribution> = emptySet(),
        memberships: Set<net.blueshell.api.domain.user.persistence.Membership> = emptySet(),
        newsletter: Boolean = false,
    ): User = mockk<User>().also {
        every { it.roles } returns roles.toMutableSet()
        every { it.committeeMembers } returns committeeMembers
        every { it.contributions } returns contributions
        every { it.memberships } returns memberships
        every { it.newsletter } returns newsletter
    }

    private fun committeeMembership(committeeId: Long?): CommitteeMember {
        val committee = mockk<Committee>()
        every { committee.id } returns committeeId
        val cm = mockk<CommitteeMember>()
        every { cm.committee } returns committee
        return cm
    }

    private fun contribution(periodId: Long): Contribution {
        val c = mockk<Contribution>()
        every { c.id } returns Contribution.Id(userId = 1L, contributionPeriodId = periodId)
        return c
    }

    @Test
    fun `emits MEMBER_IN_PERIOD for each period a membership overlaps`() {
        val pastPeriod = periodFixture(id = 10L, start = "2023-09-01", end = "2024-08-31")
        val currentPeriod = periodFixture(id = 11L, start = "2024-09-01", end = "2025-08-31")
        val futurePeriod = periodFixture(id = 12L, start = "2025-09-01", end = "2026-08-31")
        every { periods.findAll() } returns mutableListOf(pastPeriod, currentPeriod, futurePeriod)

        every { users.findById(1L) } returns userWith(
            memberships = setOf(membership(start = "2024-06-01", end = "2024-12-31")),
        )

        val facts = collector.collect(1L)

        assertThat(facts).contains(
            UserFact(CohortFactKind.MEMBER_IN_PERIOD, "10"),
            UserFact(CohortFactKind.MEMBER_IN_PERIOD, "11"),
        )
        assertThat(facts).doesNotContain(UserFact(CohortFactKind.MEMBER_IN_PERIOD, "12"))
    }

    @Test
    fun `emits ACTIVE_IN_PERIOD for each period a committee membership overlaps`() {
        val period2024 = periodFixture(id = 20L, start = "2024-09-01", end = "2025-08-31")
        val period2025 = periodFixture(id = 21L, start = "2025-09-01", end = "2026-08-31")
        every { periods.findAll() } returns mutableListOf(period2024, period2025)

        every { users.findById(1L) } returns userWith()
        every { committeeMembers.findMembershipWindowsForUser(1L) } returns listOf(
            CommitteeMembershipWindow(
                committeeId = 99L,
                joinedAt = Instant.parse("2025-01-15T00:00:00Z"),
                leftAt = Instant.parse("2025-06-30T00:00:00Z"),
            ),
        )

        val facts = collector.collect(1L)

        assertThat(facts).contains(UserFact(CohortFactKind.ACTIVE_IN_PERIOD, "20"))
        assertThat(facts).doesNotContain(UserFact(CohortFactKind.ACTIVE_IN_PERIOD, "21"))
    }

    @Test
    fun `loads the user exactly once`() {
        every { users.findById(1L) } returns userWith(roles = setOf(Role.MEMBER))

        collector.collect(1L)

        verify(exactly = 1) { users.findById(1L) }
    }

    @Test
    fun `does not query periods when the user has no memberships or committee windows`() {
        every { users.findById(1L) } returns userWith(roles = setOf(Role.MEMBER))

        collector.collect(1L)

        verify(exactly = 0) { periods.findAll() }
    }

    @Test
    fun `queries periods once when there is a membership candidate`() {
        every { periods.findAll() } returns mutableListOf(periodFixture(10L, "2023-09-01", "2024-08-31"))
        every { users.findById(1L) } returns userWith(memberships = setOf(membership("2024-01-01", "2024-06-01")))

        collector.collect(1L)

        verify(exactly = 1) { periods.findAll() }
    }

    @Test
    fun `open-ended membership overlaps periods up to the given today`() {
        val today = LocalDate.parse("2025-03-01")
        every { periods.findAll() } returns mutableListOf(
            periodFixture(id = 10L, start = "2023-09-01", end = "2024-08-31"),
            periodFixture(id = 11L, start = "2024-09-01", end = "2025-08-31"),
            periodFixture(id = 12L, start = "2025-09-01", end = "2026-08-31"),
        )
        every { users.findById(1L) } returns userWith(
            memberships = setOf(membership(start = "2024-01-01", end = null)),
        )

        val facts = collector.collect(1L, today)

        // The open-ended membership extends to today (2025-03-01), so it overlaps the
        // 2023-24 and 2024-25 periods but not the period starting 2025-09.
        assertThat(facts).contains(
            UserFact(CohortFactKind.MEMBER_IN_PERIOD, "10"),
            UserFact(CohortFactKind.MEMBER_IN_PERIOD, "11"),
        )
        assertThat(facts).doesNotContain(UserFact(CohortFactKind.MEMBER_IN_PERIOD, "12"))
    }

    private fun periodFixture(id: Long, start: String, end: String): ContributionPeriod {
        val p = mockk<ContributionPeriod>()
        every { p.id } returns id
        every { p.startDate } returns LocalDate.parse(start)
        every { p.endDate } returns LocalDate.parse(end)
        return p
    }

    private fun membership(start: String, end: String?): Membership {
        val m = mockk<Membership>()
        every { m.startDate } returns LocalDate.parse(start)
        every { m.endDate } returns end?.let { LocalDate.parse(it) }
        return m
    }
}

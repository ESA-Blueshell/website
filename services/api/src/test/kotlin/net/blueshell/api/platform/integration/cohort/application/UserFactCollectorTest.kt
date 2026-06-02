package net.blueshell.api.platform.integration.cohort.application

import io.mockk.every
import io.mockk.mockk
import net.blueshell.api.domain.committee.persistence.Committee
import net.blueshell.api.domain.committee.persistence.CommitteeMember
import net.blueshell.api.domain.contribution.application.ContributionPeriodService
import net.blueshell.api.domain.contribution.persistence.Contribution
import net.blueshell.api.domain.user.application.UserService
import net.blueshell.api.domain.user.persistence.User
import net.blueshell.api.platform.integration.cohort.persistence.CohortFactKind
import net.blueshell.api.shared.enums.Role
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class UserFactCollectorTest {

    private val users: UserService = mockk()
    private val periods: ContributionPeriodService = mockk(relaxed = true)
    private val collector = UserFactCollector(users, periods)

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
        newsletter: Boolean = false,
    ): User = mockk<User>().also {
        every { it.roles } returns roles.toMutableSet()
        every { it.committeeMembers } returns committeeMembers
        every { it.contributions } returns contributions
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
}

package net.blueshell.api.cohort.domain

import io.mockk.every
import io.mockk.mockk
import net.blueshell.api.cohort.persistence.CohortCategory
import net.blueshell.api.cohort.persistence.CohortType
import net.blueshell.api.committee.persistence.Committee
import net.blueshell.api.contribution.persistence.ContributionPeriod
import net.blueshell.api.user.api.MembershipService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.LocalDate

class CohortDefinitionsTest {
    private fun period(
        id: Long,
        from: LocalDate,
        to: LocalDate,
    ): ContributionPeriod =
        ContributionPeriod(startDate = from, endDate = to, halfYearCutoffDate = from.plusMonths(6)).apply { this.id = id }

    private val year = period(14L, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31))

    @Test
    fun `a cohort is named by its type and the thing it is about`() {
        val memberships: MembershipService = mockk()

        val definition = PeriodMembersDefinition(year, memberships)

        assertThat(definition.key).isEqualTo("PERIOD_MEMBERS:14")
        assertThat(definition.type).isEqualTo(CohortType.PERIOD_MEMBERS)
        assertThat(definition.scope).isEqualTo(14L)
        assertThat(definition.label).isEqualTo("Members 2026 - 2026")
    }

    @Test
    fun `member-in-period asks about the period's own dates, both ways round`() {
        val memberships: MembershipService = mockk()
        every { memberships.findUserIdsOverlapping(year.startDate, year.endDate) } returns setOf(1L, 2L)
        every { memberships.heldMembershipBetween(3L, year.startDate, year.endDate) } returns true

        val definition = PeriodMembersDefinition(year, memberships)

        assertThat(definition.members()).containsExactlyInAnyOrder(1L, 2L)
        assertThat(definition.contains(3L)).isTrue()
    }

    private class FakeSource(
        private val ids: Set<Long>,
    ) : PeriodActivitySource {
        override fun activeBetween(
            from: LocalDate,
            to: LocalDate,
        ): Set<Long> = ids

        override fun wasActive(
            userId: Long,
            from: LocalDate,
            to: LocalDate,
        ): Boolean = userId in ids
    }

    @Test
    fun `being active is being active by any one source`() {
        val committees = FakeSource(setOf(1L, 2L))
        val boards = FakeSource(setOf(3L))
        val teams = FakeSource(setOf(2L, 4L))

        val definition = PeriodActiveMembersDefinition(year, listOf(committees, boards, teams))

        // The union, and 2 counted once for being in two of them.
        assertThat(definition.members()).containsExactlyInAnyOrder(1L, 2L, 3L, 4L)
        assertThat(definition.contains(3L)).isTrue()
        assertThat(definition.contains(4L)).isTrue()
        assertThat(definition.contains(99L)).isFalse()
    }

    @Test
    fun `with no sources at all, nobody is active`() {
        val definition = PeriodActiveMembersDefinition(year, emptyList())

        assertThat(definition.members()).isEmpty()
        assertThat(definition.contains(1L)).isFalse()
    }

    @Test
    fun `a source that knows nobody does not stop the others counting`() {
        val definition =
            PeriodActiveMembersDefinition(
                year,
                listOf(FakeSource(emptySet()), FakeSource(setOf(7L))),
            )

        assertThat(definition.members()).containsExactly(7L)
        assertThat(definition.contains(7L)).isTrue()
    }

    @Test
    fun `each cohort type names its own Brevo folder`() {
        val committee = Committee(name = "Sitecie", description = "Builds the site").apply { id = 3L }

        assertThat(PeriodMembersDefinition(year, mockk()).folder).isEqualTo(CohortFolders.MEMBERS)
        assertThat(PeriodPayersDefinition(year, mockk()).folder).isEqualTo(CohortFolders.CONTRIBUTION_PAID)
        assertThat(PeriodActiveMembersDefinition(year, emptyList()).folder).isEqualTo(CohortFolders.ACTIVE_MEMBERS)
        assertThat(CommitteeMembersDefinition(committee, mockk()).folder).isEqualTo(CohortFolders.COMMITTEES)
        // An archived committee keeps its seats and holds nobody.
        committee.archived = true
        assertThat(CommitteeMembersDefinition(committee, mockk()).members()).isEmpty()
        assertThat(NewsletterSubscribersDefinition(mockk()).folder).isEqualTo(CohortFolders.NEWSLETTER)
    }

    @Test
    fun `each provider says which type it produces, and each type which category it browses under`() {
        val providers =
            listOf(
                PeriodMembersProvider(mockk(), mockk()),
                PeriodPayersProvider(mockk(), mockk()),
                PeriodActiveMembersProvider(mockk(), emptyList()),
                CommitteeMembersProvider(mockk(), mockk()),
                NewsletterSubscribersProvider(mockk()),
                ActivistsProvider(mockk(), mockk(), mockk()),
                CurrentMembersProvider(mockk()),
                TeamPlayersProvider(mockk()),
                BoardProvider(mockk()),
                KandiProvider(mockk()),
                BoardYearProvider(mockk()),
            )

        assertThat(providers.map { it.type }).containsExactlyInAnyOrder(*CohortType.entries.toTypedArray())
        assertThat(CohortType.entries.associateWith { it.category() }).isEqualTo(
            mapOf(
                CohortType.COMMITTEE_MEMBERS to CohortCategory.COMMITTEES,
                CohortType.PERIOD_PAYERS to CohortCategory.PERIODS,
                CohortType.PERIOD_MEMBERS to CohortCategory.PERIODS,
                CohortType.PERIOD_ACTIVE_MEMBERS to CohortCategory.PERIODS,
                CohortType.NEWSLETTER_SUBSCRIBERS to CohortCategory.MEMBERS,
                CohortType.ACTIVISTS to CohortCategory.MEMBERS,
                CohortType.CURRENT_MEMBERS to CohortCategory.MEMBERS,
                CohortType.TEAM_PLAYERS to CohortCategory.TEAMS,
                CohortType.BOARD to CohortCategory.MEMBERS,
                CohortType.KANDI to CohortCategory.MEMBERS,
                CohortType.BOARD_YEAR_MEMBERS to CohortCategory.MEMBERS,
            ),
        )
    }
}

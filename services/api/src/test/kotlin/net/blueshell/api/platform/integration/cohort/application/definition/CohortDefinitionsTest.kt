package net.blueshell.api.platform.integration.cohort.application.definition

import io.mockk.every
import io.mockk.mockk
import net.blueshell.api.domain.contribution.persistence.ContributionPeriod
import net.blueshell.api.domain.user.application.MembershipService
import net.blueshell.api.platform.integration.cohort.persistence.CohortSubjectType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.LocalDate

class CohortDefinitionsTest {

    private fun period(id: Long, from: LocalDate, to: LocalDate): ContributionPeriod =
        ContributionPeriod(startDate = from, endDate = to).apply { this.id = id }

    private val year = period(14L, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31))

    @Test
    fun `a cohort is named by its type and the thing it is about`() {
        val memberships: MembershipService = mockk()

        val definition = PeriodMembersDefinition(year, memberships)

        assertThat(definition.key).isEqualTo("PERIOD_MEMBERS:14")
        assertThat(definition.type).isEqualTo(CohortSubjectType.PERIOD_MEMBERS)
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

    // ── Active in a period ───────────────────────────────────────────────────

    private class FakeSource(private val ids: Set<Long>) : PeriodActivitySource {
        override fun activeBetween(from: LocalDate, to: LocalDate): Set<Long> = ids
        override fun wasActive(userId: Long, from: LocalDate, to: LocalDate): Boolean = userId in ids
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
        val definition = PeriodActiveMembersDefinition(
            year,
            listOf(FakeSource(emptySet()), FakeSource(setOf(7L))),
        )

        assertThat(definition.members()).containsExactly(7L)
        assertThat(definition.contains(7L)).isTrue()
    }
}

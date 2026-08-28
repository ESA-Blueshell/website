package net.blueshell.api.platform.integration.cohort.application

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.blueshell.api.contribution.api.ContributionService
import net.blueshell.api.platform.integration.cohort.application.definition.CohortDefinition
import net.blueshell.api.platform.integration.cohort.persistence.CohortSubjectType
import net.blueshell.api.platform.integration.cohort.port.`in`.CohortReconciliation
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class MembershipWritersTest {
    private val contributions: ContributionService = mockk(relaxed = true)
    private val reconciliation: CohortReconciliation = mockk(relaxed = true)
    private val writer = ContributionPaidWriter(contributions, reconciliation)
    private val writers = MembershipWriters(listOf(writer))

    /** The paid cohort for period 12, which is the only kind anything can write into. */
    private fun paidCohort(): CohortDefinition = mockk<CohortDefinition>().also {
        every { it.key } returns "PERIOD_PAYERS:12"
        every { it.type } returns CohortSubjectType.PERIOD_PAYERS
        every { it.scope } returns 12L
    }

    @Test
    fun `only the cohorts that can be written into have a writer`() {
        assertThat(writers.find(CohortSubjectType.PERIOD_PAYERS)).isSameAs(writer)
        // Nobody can be made a member of the newsletter by writing something else true.
        assertThat(writers.find(CohortSubjectType.NEWSLETTER_SUBSCRIBERS)).isNull()
    }

    @Test
    fun `a member who already belongs is previewed as such, and nothing is written`() {
        val definition = paidCohort()
        every { definition.contains(5L) } returns true

        val result = writer.preview(5L, definition)

        assertThat(result.alreadyMember).isTrue()
        verify(exactly = 0) { contributions.ensurePaid(any(), any()) }
        verify(exactly = 0) { reconciliation.evaluateUserCohorts(any()) }
    }

    @Test
    fun `applying records the contribution once, and says so the second time`() {
        val definition = paidCohort()
        every { definition.contains(5L) } returnsMany listOf(false, true)
        every { contributions.ensurePaid(5L, 12L) } returns true

        val first = writer.apply(5L, definition)
        val second = writer.apply(5L, definition)

        assertThat(first).isEqualTo(MembershipWriteStatus.WRITTEN)
        assertThat(second).isEqualTo(MembershipWriteStatus.NOOP_ALREADY_TRUE)
        verify(exactly = 1) { contributions.ensurePaid(5L, 12L) }
        // Either way the member is re-evaluated, so the ledger follows what just changed.
        verify(exactly = 2) { reconciliation.evaluateUserCohorts(5L) }
    }
}

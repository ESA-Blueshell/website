package net.blueshell.api.platform.integration.cohort.application

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.blueshell.api.domain.contribution.application.ContributionService
import net.blueshell.api.platform.integration.cohort.persistence.CohortFactKind
import net.blueshell.api.platform.integration.cohort.port.`in`.CohortReconciliation
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class FactWritersTest {
    private val facts: UserFactCollector = mockk()
    private val contributions: ContributionService = mockk(relaxed = true)
    private val reconciliation: CohortReconciliation = mockk(relaxed = true)
    private val writer = ContributionPaidWriter(facts, contributions, reconciliation)
    private val writers = FactWriters(listOf(writer))

    @Test
    fun `registry resolves contribution paid and misses unsupported kinds`() {
        assertThat(writers.find(CohortFactKind.CONTRIBUTION_PAID)).isSameAs(writer)
        assertThat(writers.find(CohortFactKind.NEWSLETTER)).isNull()
    }

    @Test
    fun `contribution preview reports already true without writing`() {
        every { facts.collect(5L) } returns setOf(SubjectFact(CohortFactKind.CONTRIBUTION_PAID, "12").toUserFact())

        val result = writer.preview(5L, SubjectFact(CohortFactKind.CONTRIBUTION_PAID, "12"))

        assertThat(result.alreadyTrue).isTrue()
        verify(exactly = 0) { contributions.ensurePaid(any(), any()) }
        verify(exactly = 0) { reconciliation.evaluateUserCohorts(any()) }
    }

    @Test
    fun `contribution apply creates once then noops once true`() {
        every { facts.collect(5L) } returnsMany listOf(
            emptySet(),
            setOf(SubjectFact(CohortFactKind.CONTRIBUTION_PAID, "12").toUserFact()),
        )
        every { contributions.ensurePaid(5L, 12L) } returns true

        val first = writer.apply(5L, SubjectFact(CohortFactKind.CONTRIBUTION_PAID, "12"))
        val second = writer.apply(5L, SubjectFact(CohortFactKind.CONTRIBUTION_PAID, "12"))

        assertThat(first).isEqualTo(FactWriteStatus.WRITTEN)
        assertThat(second).isEqualTo(FactWriteStatus.NOOP_ALREADY_TRUE)
        verify(exactly = 1) { contributions.ensurePaid(5L, 12L) }
        verify(exactly = 2) { reconciliation.evaluateUserCohorts(5L) }
    }
}

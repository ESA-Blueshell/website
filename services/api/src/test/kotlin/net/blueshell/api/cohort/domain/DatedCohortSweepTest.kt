package net.blueshell.api.cohort.domain

import net.blueshell.api.cohort.persistence.CohortType
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class DatedCohortSweepTest {
    private fun definition(type: CohortType): CohortDefinition =
        mock {
            on { this.type } doReturn type
            on { key } doReturn type.name
        }

    @Test
    fun `recomputes the cohorts read from today's date, carrying on past one that fails`() {
        val board = definition(CohortType.BOARD)
        val kandi = definition(CohortType.KANDI)
        val activists = definition(CohortType.ACTIVISTS)
        val members = definition(CohortType.CURRENT_MEMBERS)
        val committee = definition(CohortType.COMMITTEE_MEMBERS)
        val registry: CohortDefinitionRegistry = mock { on { all() } doReturn listOf(board, kandi, activists, members, committee) }
        val updater: CohortMembershipUpdater = mock()
        doThrow(IllegalStateException("boom")).whenever(updater).updateCohort(board)

        DatedCohortSweep(registry, updater).recompute()

        listOf(board, kandi, activists, members).forEach { verify(updater).updateCohort(it) }
        verify(updater, never()).updateCohort(committee)
    }
}

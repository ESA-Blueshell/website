package net.blueshell.api.cohort.domain

import net.blueshell.api.cohort.persistence.CohortType
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.inOrder
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
        val year = definition(CohortType.BOARD_YEAR_MEMBERS)
        val committee = definition(CohortType.COMMITTEE_MEMBERS)
        val registry: CohortDefinitionRegistry = mock { on { all() } doReturn listOf(board, kandi, activists, members, year, committee) }
        val updater: CohortMembershipUpdater = mock()
        val registrar: CohortRegistrar = mock()
        doThrow(IllegalStateException("boom")).whenever(updater).updateCohort(board)

        DatedCohortSweep(registry, updater, registrar).recompute()

        // A board whose year begins today gets its cohort before anything is recomputed.
        inOrder(registrar, updater) {
            verify(registrar).register()
            verify(updater).updateCohort(board)
        }
        listOf(kandi, activists, members, year).forEach { verify(updater).updateCohort(it) }
        verify(updater, never()).updateCohort(committee)
    }

    @Test
    fun `recomputes even when today's cohorts cannot be registered`() {
        val board = definition(CohortType.BOARD)
        val registry: CohortDefinitionRegistry = mock { on { all() } doReturn listOf(board) }
        val updater: CohortMembershipUpdater = mock()
        val registrar: CohortRegistrar = mock { on { register() } doThrow IllegalStateException("database is down") }

        DatedCohortSweep(registry, updater, registrar).recompute()

        verify(updater).updateCohort(board)
    }
}

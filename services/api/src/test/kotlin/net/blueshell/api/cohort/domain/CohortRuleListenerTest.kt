package net.blueshell.api.cohort.domain

import io.mockk.mockk
import io.mockk.verify
import net.blueshell.api.committee.api.CommitteeCreated
import net.blueshell.api.contribution.api.ContributionPeriodChanged
import org.junit.jupiter.api.Test

class CohortRuleListenerTest {
    private val updater: CohortMembershipUpdater = mockk(relaxed = true)
    private val registrar: CohortRegistrar = mockk(relaxed = true)
    private val listener = CohortRuleListener(updater, registrar)

    @Test
    fun `a new committee registers its cohort without touching anyone's membership`() {
        listener.onCommitteeCreated(CommitteeCreated(7))

        verify(exactly = 1) { registrar.register() }
        verify(exactly = 0) { updater.updateMember(any()) }
    }

    @Test
    fun `a new or changed contribution period registers its cohorts`() {
        listener.onContributionPeriodChanged(ContributionPeriodChanged(14))

        verify(exactly = 1) { registrar.register() }
    }
}

package net.blueshell.api.cohort.domain

import io.mockk.mockk
import io.mockk.verify
import net.blueshell.api.board.api.BoardMembershipChanged
import net.blueshell.api.committee.api.CommitteeArchiveChanged
import net.blueshell.api.committee.api.CommitteeCreated
import net.blueshell.api.contribution.api.ContributionPeriodChanged
import net.blueshell.api.esports.api.RosterChanged
import org.junit.jupiter.api.Test

class CohortRuleListenerTest {
    private val updater: CohortMembershipUpdater = mockk(relaxed = true)
    private val registrar: CohortRegistrar = mockk(relaxed = true)
    private val discord: CohortDiscord = mockk(relaxed = true)
    private val listener = CohortRuleListener(updater, registrar, discord, "Committees")

    @Test
    fun `a new committee registers its cohort without touching anyone's membership`() {
        listener.onCommitteeCreated(CommitteeCreated(7))

        verify(exactly = 1) { registrar.register() }
        verify(exactly = 0) { updater.updateMember(any()) }
    }

    @Test
    fun `a line-up change registers a new team's cohort and looks at who came on or off`() {
        listener.onRosterChanged(RosterChanged(3, setOf(7L, 8L)))

        verify { registrar.register() }
        verify { updater.updateMember(7L) }
        verify { updater.updateMember(8L) }
    }

    @Test
    fun `a committee archived or brought back moves its channels`() {
        listener.onCommitteeArchiveChanged(CommitteeArchiveChanged(7, archived = true))

        verify { discord.archive("COMMITTEE_MEMBERS:7", true, "Committees") }
    }

    @Test
    fun `a board place gained, changed or given up looks at that person's cohorts again`() {
        listener.onBoardMembershipChanged(BoardMembershipChanged(7))

        verify(exactly = 1) { updater.updateMember(7) }
    }

    @Test
    fun `a new or changed contribution period registers its cohorts`() {
        listener.onContributionPeriodChanged(ContributionPeriodChanged(14))

        verify(exactly = 1) { registrar.register() }
    }
}

package net.blueshell.api.cohort.domain

import net.blueshell.api.board.api.BoardMembershipChanged
import net.blueshell.api.committee.api.CommitteeCreated
import net.blueshell.api.committee.api.CommitteeMembershipChanged
import net.blueshell.api.contribution.api.ContributionChanged
import net.blueshell.api.contribution.api.ContributionPeriodChanged
import net.blueshell.api.esports.api.RosterChanged
import net.blueshell.api.shared.event.AfterCommitListener
import net.blueshell.api.user.api.MembershipChanged
import net.blueshell.api.user.api.UserCreated
import net.blueshell.api.user.api.UserDeleted
import net.blueshell.api.user.api.UserUpdated
import org.springframework.stereotype.Component

/**
 * Funnels every change that can alter who belongs where into one re-evaluation of that
 * member's cohorts.
 *
 * A new committee or contribution period brings a definition with it, so those two events
 * register first: without a cohort record behind the definition there is nothing to write the
 * membership against. `REQUIRES_NEW` as elsewhere, so a failure here cannot roll back the
 * change that caused it.
 */
@Component
class CohortRuleListener(
    private val updater: CohortMembershipUpdater,
    private val registrar: CohortRegistrar,
) {
    @AfterCommitListener
    fun onUserCreated(evt: UserCreated) {
        updater.updateMember(evt.userId)
    }

    @AfterCommitListener
    fun onUserUpdated(evt: UserUpdated) {
        updater.updateMember(evt.userId)
    }

    @AfterCommitListener
    fun onUserDeleted(evt: UserDeleted) {
        // A deleted member belongs to no definition, so the diff is "remove from everything",
        // which is what takes them off the external lists.
        updater.updateMember(evt.userId)
    }

    @AfterCommitListener
    fun onMembershipChanged(evt: MembershipChanged) {
        updater.updateMember(evt.userId)
    }

    @AfterCommitListener
    fun onCommitteeMembershipChanged(evt: CommitteeMembershipChanged) {
        // A committee seated for the first time has a definition but no record yet.
        registrar.register()
        updater.updateMember(evt.userId)
    }

    @AfterCommitListener
    fun onRosterChanged(evt: RosterChanged) {
        // A team on its first line-up has a definition but no record yet.
        registrar.register()
        evt.userIds.forEach(updater::updateMember)
    }

    @AfterCommitListener
    fun onBoardMembershipChanged(evt: BoardMembershipChanged) {
        updater.updateMember(evt.userId)
    }

    // Only the event's type matters: a new committee or period brings a definition to register.
    @Suppress("UnusedParameter")
    @AfterCommitListener
    fun onCommitteeCreated(evt: CommitteeCreated) {
        registrar.register()
    }

    @Suppress("UnusedParameter")
    @AfterCommitListener
    fun onContributionPeriodChanged(evt: ContributionPeriodChanged) {
        registrar.register()
    }

    @AfterCommitListener
    fun onContributionChanged(evt: ContributionChanged) {
        registrar.register()
        updater.updateMember(evt.userId)
    }
}

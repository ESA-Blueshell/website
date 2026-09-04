package net.blueshell.api.cohort.domain

import net.blueshell.api.committee.api.CommitteeMembershipChanged
import net.blueshell.api.contribution.api.ContributionChanged
import net.blueshell.api.user.api.MembershipChanged
import net.blueshell.api.user.api.UserCreated
import net.blueshell.api.user.api.UserDeleted
import net.blueshell.api.user.api.UserUpdated
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

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
    @EventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun onUserCreated(evt: UserCreated) {
        updater.updateMember(evt.userId)
    }

    @EventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun onUserUpdated(evt: UserUpdated) {
        updater.updateMember(evt.userId)
    }

    @EventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun onUserDeleted(evt: UserDeleted) {
        // A deleted member belongs to no definition, so the diff is "remove from everything",
        // which is what takes them off the external lists.
        updater.updateMember(evt.userId)
    }

    @EventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun onMembershipChanged(evt: MembershipChanged) {
        updater.updateMember(evt.userId)
    }

    @EventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun onCommitteeMembershipChanged(evt: CommitteeMembershipChanged) {
        // A committee seated for the first time has a definition but no record yet.
        registrar.register()
        updater.updateMember(evt.userId)
    }

    @EventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun onContributionChanged(evt: ContributionChanged) {
        registrar.register()
        updater.updateMember(evt.userId)
    }
}

package net.blueshell.api.committee.domain

import net.blueshell.api.committee.api.CommitteeMemberService
import net.blueshell.api.committee.api.CommitteeMembershipChanged
import net.blueshell.api.domain.user.application.UserService
import net.blueshell.api.shared.enums.Role
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

@Component
class CommitteeMembershipChangedListener(
    private val committeeMemberService: CommitteeMemberService,
    private val users: UserService
) {
    @EventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun onChange(event: CommitteeMembershipChanged) {
        if (committeeMemberService.countMembershipsForUser(event.userId) > 0) {
            users.addRole(event.userId, Role.COMMITTEE)
        } else {
            users.removeRole(event.userId, Role.COMMITTEE)
        }
    }
}

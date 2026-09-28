package net.blueshell.api.committee.domain

import net.blueshell.api.committee.api.CommitteeMemberService
import net.blueshell.api.committee.api.CommitteeMembershipChanged
import net.blueshell.api.shared.enums.Role
import net.blueshell.api.shared.event.AfterCommitListener
import net.blueshell.api.user.api.UserService
import org.springframework.stereotype.Component

@Component
class CommitteeMembershipChangedListener(
    private val committeeMemberService: CommitteeMemberService,
    private val users: UserService,
) {
    @AfterCommitListener
    fun onChange(event: CommitteeMembershipChanged) {
        if (committeeMemberService.countMembershipsForUser(event.userId) > 0) {
            users.addRole(event.userId, Role.COMMITTEE)
        } else {
            users.removeRole(event.userId, Role.COMMITTEE)
        }
    }
}

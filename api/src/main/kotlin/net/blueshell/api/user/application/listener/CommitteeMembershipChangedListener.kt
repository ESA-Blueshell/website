package net.blueshell.api.user.application.listener

import net.blueshell.api.committee.application.event.CommitteeMembershipChanged
import net.blueshell.api.committee.persistence.repository.CommitteeMemberRepository
import net.blueshell.api.shared.enums.Role
import net.blueshell.api.user.application.UserService
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

@Component
class CommitteeMembershipChangedListener(
    private val committeeMembers: CommitteeMemberRepository,
    private val users: UserService
) {
    @EventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun onChange(event: CommitteeMembershipChanged) {
        if (committeeMembers.countByUserId(event.userId) > 0) {
            users.addRole(event.userId, Role.COMMITTEE)
        } else {
            users.removeRole(event.userId, Role.COMMITTEE)
        }
    }
}

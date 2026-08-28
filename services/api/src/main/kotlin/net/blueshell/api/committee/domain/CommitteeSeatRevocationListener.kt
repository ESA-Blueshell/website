package net.blueshell.api.committee.domain

import net.blueshell.api.committee.api.CommitteeMemberService
import net.blueshell.api.domain.user.application.UserService
import net.blueshell.api.domain.user.application.event.UserUpdated
import net.blueshell.api.shared.enums.Role
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

/** Gives up a user's committee seats once they no longer hold the MEMBER role. */
@Component
class CommitteeSeatRevocationListener(
    private val users: UserService,
    private val committeeMembers: CommitteeMemberService,
) {
    @EventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun onUpdate(evt: UserUpdated) {
        if (!users.findById(evt.userId).hasAuthority(Role.MEMBER)) {
            committeeMembers.revokeAllSeatsForUser(evt.userId)
        }
    }
}

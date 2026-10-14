package net.blueshell.api.user.domain

import net.blueshell.api.shared.event.AfterCommitListener
import net.blueshell.api.user.api.MembershipChanged
import net.blueshell.api.user.api.MembershipService
import org.springframework.stereotype.Component

/**
 * A mandate ends on the day the member's last running membership ends, so rejoining starts
 * without one (api ADR-040). A mandate set up during signup waits for its first membership.
 */
@Component
class MandateEndsWithMembership(
    private val memberships: MembershipService,
    private val mandates: Mandates,
) {
    @AfterCommitListener
    fun onChange(evt: MembershipChanged) {
        if (evt.changeType == MembershipChange.CREATED) return
        val held = memberships.findByUserId(evt.userId)
        if (held.any { it.endDate == null }) return
        mandates.end(evt.userId, held.mapNotNull { it.endDate }.maxOrNull())
    }
}

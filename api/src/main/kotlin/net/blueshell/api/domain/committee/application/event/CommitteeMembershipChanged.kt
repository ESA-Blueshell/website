package net.blueshell.api.domain.committee.application.event

import net.blueshell.api.shared.tracking.Actor
import net.blueshell.api.shared.tracking.ActorTracked

data class CommitteeMembershipChanged(
    val userId: Long,
    val committeeId: Long,
    override val actor: Actor = Actor.system()
) : ActorTracked

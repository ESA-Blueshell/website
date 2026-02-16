package net.blueshell.api.domain.membership.application.event

import net.blueshell.api.shared.tracking.Actor
import net.blueshell.api.shared.tracking.ActorTracked

data class MembershipChanged(
    val userId: Long,
    val active: Boolean,
    val changeType: MembershipChange,
    override val actor: Actor = Actor.system()
) : ActorTracked

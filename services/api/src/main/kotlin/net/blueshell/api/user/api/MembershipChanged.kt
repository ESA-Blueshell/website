package net.blueshell.api.user.api

import net.blueshell.api.shared.tracking.Actor
import net.blueshell.api.shared.tracking.ActorTracked
import net.blueshell.api.user.domain.MembershipChange

data class MembershipChanged(
    val userId: Long,
    val active: Boolean,
    val changeType: MembershipChange,
    override val actor: Actor = Actor.system()
) : ActorTracked

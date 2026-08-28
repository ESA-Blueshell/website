package net.blueshell.api.user.domain

import net.blueshell.api.shared.tracking.Actor
import net.blueshell.api.shared.tracking.ActorTracked

data class UserRestored(
    val userId: Long,
    override val actor: Actor = Actor.system()
) : ActorTracked

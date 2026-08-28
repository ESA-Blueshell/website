package net.blueshell.api.user.api

import net.blueshell.api.shared.tracking.Actor
import net.blueshell.api.shared.tracking.ActorTracked

data class UserDeleted(
    val userId: Long,
    override val actor: Actor = Actor.system()
) : ActorTracked

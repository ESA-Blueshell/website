package net.blueshell.api.user.api

import net.blueshell.api.shared.tracking.Actor
import net.blueshell.api.shared.tracking.ActorTracked

data class UserCreated(
    val userId: Long,
    val createdByBoard: Boolean? = null,
    override val actor: Actor = Actor.system()
) : ActorTracked

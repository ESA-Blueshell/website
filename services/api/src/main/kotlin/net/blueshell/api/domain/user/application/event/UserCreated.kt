package net.blueshell.api.domain.user.application.event

import net.blueshell.api.shared.tracking.Actor
import net.blueshell.api.shared.tracking.ActorTracked

data class UserCreated(
    val userId: Long,
    val createdByBoard: Boolean? = null,
    override val actor: Actor = Actor.system()
) : ActorTracked

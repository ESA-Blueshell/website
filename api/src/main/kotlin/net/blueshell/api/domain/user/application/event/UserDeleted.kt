package net.blueshell.api.domain.user.application.event

import net.blueshell.api.shared.tracking.Actor
import net.blueshell.api.shared.tracking.ActorTracked

data class UserDeleted(
    val userId: Long,
    override val actor: Actor = Actor.system()
) : ActorTracked

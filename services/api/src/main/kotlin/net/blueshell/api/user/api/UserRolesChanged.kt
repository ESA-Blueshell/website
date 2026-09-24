package net.blueshell.api.user.api

import net.blueshell.api.shared.tracking.Actor
import net.blueshell.api.shared.tracking.ActorTracked

/** An admin changed the roles somebody was granted. */
data class UserRolesChanged(
    val userId: Long,
    override val actor: Actor = Actor.system(),
) : ActorTracked

package net.blueshell.api.user.api

import net.blueshell.api.shared.enums.Role
import net.blueshell.api.shared.tracking.Actor
import net.blueshell.api.shared.tracking.ActorTracked

/**
 * An admin changed the roles somebody was granted. [dormantGranted] names the roles just granted
 * that wait on two-factor, because the person has none.
 */
data class UserRolesChanged(
    val userId: Long,
    override val actor: Actor = Actor.system(),
    val dormantGranted: Set<Role> = emptySet(),
) : ActorTracked

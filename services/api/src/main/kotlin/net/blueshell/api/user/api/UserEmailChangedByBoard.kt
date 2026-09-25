package net.blueshell.api.user.api

import net.blueshell.api.shared.tracking.Actor
import net.blueshell.api.shared.tracking.ActorTracked

/** A board member moved somebody's account to another address; the old one is to be told. */
data class UserEmailChangedByBoard(
    val userId: Long,
    val oldEmail: String,
    override val actor: Actor = Actor.system(),
) : ActorTracked

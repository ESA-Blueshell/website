package net.blueshell.api.board.api

import net.blueshell.api.shared.tracking.Actor
import net.blueshell.api.shared.tracking.ActorTracked

/** Somebody's place on a board was added, changed or removed. */
data class BoardMembershipChanged(
    val userId: Long,
    override val actor: Actor = Actor.system(),
) : ActorTracked

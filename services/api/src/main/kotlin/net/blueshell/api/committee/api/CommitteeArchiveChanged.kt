package net.blueshell.api.committee.api

import net.blueshell.api.shared.tracking.Actor
import net.blueshell.api.shared.tracking.ActorTracked

/** A committee stopped running, or runs again. */
data class CommitteeArchiveChanged(
    val committeeId: Long,
    val archived: Boolean,
    override val actor: Actor = Actor.system(),
) : ActorTracked

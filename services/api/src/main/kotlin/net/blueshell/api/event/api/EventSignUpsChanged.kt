package net.blueshell.api.event.api

import net.blueshell.api.shared.tracking.Actor
import net.blueshell.api.shared.tracking.ActorTracked

/** Somebody signed up for the event, or a sign-up was taken off it: its count moved. */
data class EventSignUpsChanged(
    val eventId: Long,
    override val actor: Actor = Actor.system(),
) : ActorTracked

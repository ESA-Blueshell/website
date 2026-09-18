package net.blueshell.api.event.api

import net.blueshell.api.event.domain.EventChange
import net.blueshell.api.shared.tracking.Actor
import net.blueshell.api.shared.tracking.ActorTracked

data class EventChanged(
    val eventId: Long,
    val changeType: EventChange,
    override val actor: Actor = Actor.system(),
) : ActorTracked

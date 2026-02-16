package net.blueshell.api.domain.event.application.event

import net.blueshell.api.shared.tracking.Actor
import net.blueshell.api.shared.tracking.ActorTracked

data class EventSignUpCreated(
    val signUpId: Long,
    override val actor: Actor = Actor.system()
) : ActorTracked

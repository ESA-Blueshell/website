package net.blueshell.api.shared.event

import net.blueshell.api.shared.tracking.Actor
import net.blueshell.api.shared.tracking.ActorProvider
import org.springframework.stereotype.Component

@Component
class TrackedEventPublisher(
    private val events: AfterCommitEventPublisher,
    private val actors: ActorProvider
) {
    fun publish(factory: (Actor) -> Any) {
        events.publish(factory(actors.currentOrSystem()))
    }
}

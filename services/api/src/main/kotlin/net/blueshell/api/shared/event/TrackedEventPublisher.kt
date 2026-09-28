package net.blueshell.api.shared.event

import net.blueshell.api.shared.tracking.Actor
import net.blueshell.api.shared.tracking.ActorProvider
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Component

/** Publishes a domain event attributed to whoever is acting now. */
@Component
class TrackedEventPublisher(
    private val events: AfterCommitEventPublisher,
    private val actors: ActorProvider,
    private val within: ApplicationEventPublisher,
) {
    /** Once the transaction commits, so no listener sees a change that may yet roll back. */
    fun publish(factory: (Actor) -> Any) {
        events.publish(factory(actors.currentOrSystem()))
    }

    /**
     * Inside the open transaction, for a listener whose writes belong to the change, such as the
     * jobs it queues. A listener bound to the commit still runs after it.
     */
    fun publishWithin(factory: (Actor) -> Any) {
        within.publishEvent(factory(actors.currentOrSystem()))
    }
}

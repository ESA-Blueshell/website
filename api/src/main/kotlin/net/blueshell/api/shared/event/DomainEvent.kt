package net.blueshell.api.shared.event

import net.blueshell.api.shared.tracking.Actor
import java.time.Instant
import java.util.*

interface DomainEvent {
    val domainEventId: UUID
    val occurredAt: Instant
    val actor: Actor
}

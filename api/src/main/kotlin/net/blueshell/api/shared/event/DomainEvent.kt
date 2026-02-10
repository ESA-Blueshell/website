package net.blueshell.api.shared.event

import java.time.Instant
import java.util.*

interface DomainEvent {
    val domainEventId: UUID
    val occurredAt: Instant
    val actorUserId: Long?
}

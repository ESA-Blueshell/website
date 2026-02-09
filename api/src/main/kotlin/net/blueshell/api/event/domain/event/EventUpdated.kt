package net.blueshell.api.event.domain.event

import net.blueshell.api.shared.event.DomainEvent
import java.time.Instant
import java.util.UUID

data class EventUpdated(
    val eventId: Long,
    val approved: Boolean,
    override val actorUserId: Long? = null,
    override val domainEventId: UUID = UUID.randomUUID(),
    override val occurredAt: Instant = Instant.now()
) : DomainEvent

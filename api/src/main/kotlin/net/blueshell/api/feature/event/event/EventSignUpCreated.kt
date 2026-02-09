package net.blueshell.api.feature.event.event

import net.blueshell.api.shared.event.DomainEvent
import java.time.Instant
import java.util.UUID

data class EventSignUpCreated(
    val eventSignUpId: Long,
    val eventId: Long,
    val userId: Long?,
    val guestId: Long?,
    override val actorUserId: Long? = null,
    override val domainEventId: UUID = UUID.randomUUID(),
    override val occurredAt: Instant = Instant.now()
) : DomainEvent

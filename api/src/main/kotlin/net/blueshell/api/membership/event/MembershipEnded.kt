package net.blueshell.api.membership.event

import net.blueshell.api.shared.event.DomainEvent
import java.time.Instant
import java.util.UUID

data class MembershipEnded(
    val membershipId: Long,
    val userId: Long,
    override val actorUserId: Long? = null,
    override val domainEventId: UUID = UUID.randomUUID(),
    override val occurredAt: Instant = Instant.now()
) : DomainEvent

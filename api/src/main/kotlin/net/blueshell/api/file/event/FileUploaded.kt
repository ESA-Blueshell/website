package net.blueshell.api.file.event

import net.blueshell.api.shared.event.DomainEvent
import java.time.Instant
import java.util.UUID

data class FileUploaded(
    val fileId: Long,
    val uploaderId: Long,
    override val actorUserId: Long? = null,
    override val domainEventId: UUID = UUID.randomUUID(),
    override val occurredAt: Instant = Instant.now()
) : DomainEvent

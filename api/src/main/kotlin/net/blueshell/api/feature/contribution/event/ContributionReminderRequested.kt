package net.blueshell.api.feature.contribution.event

import net.blueshell.api.shared.event.DomainEvent
import java.time.Instant
import java.util.UUID

data class ContributionReminderRequested(
    val contributionReminderId: Long,
    val userId: Long,
    val contributionPeriodId: Long,
    override val actorUserId: Long? = null,
    override val domainEventId: UUID = UUID.randomUUID(),
    override val occurredAt: Instant = Instant.now()
) : DomainEvent

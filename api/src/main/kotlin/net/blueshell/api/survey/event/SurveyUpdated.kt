package net.blueshell.api.survey.event

import net.blueshell.api.shared.event.DomainEvent
import java.time.Instant
import java.util.UUID

data class SurveyUpdated(
    val surveyId: Long,
    override val actorUserId: Long? = null,
    override val domainEventId: UUID = UUID.randomUUID(),
    override val occurredAt: Instant = Instant.now()
) : DomainEvent

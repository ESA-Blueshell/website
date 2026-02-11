package net.blueshell.api.domain.event.application.event

data class EventChanged(
    val eventId: Long,
    val changeType: EventChange
)

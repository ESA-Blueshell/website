package net.blueshell.api.event.application.event

data class EventChanged(
    val eventId: Long,
    val changeType: EventChange
)

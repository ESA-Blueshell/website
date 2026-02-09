package net.blueshell.api.event.application.event

data class EventChangedEvent(
    val eventId: Long,
    val changeType: EventChangeType
)

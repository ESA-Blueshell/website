package net.blueshell.api.shared.event

interface DomainEventPublisher {
    fun publish(event: DomainEvent)
}

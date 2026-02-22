package net.blueshell.api.shared.event

interface DomainEventListener<E : DomainEvent> {
    fun handle(event: E)
}

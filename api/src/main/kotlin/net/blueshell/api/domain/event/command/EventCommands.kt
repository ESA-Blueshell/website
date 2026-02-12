package net.blueshell.api.domain.event.command

import net.blueshell.api.domain.event.persistence.Event
import net.blueshell.api.domain.event.persistence.filter.EventFilter
import net.blueshell.api.domain.event.web.dto.EventDTO
import net.blueshell.api.shared.command.Command
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable

data class CreateEventCommand(
    val dto: EventDTO
) : Command<Event>

data class UpdateEventCommand(
    val id: Long,
    val dto: EventDTO
) : Command<Event>

data class ApproveEventCommand(
    val id: Long,
    val approved: Boolean
) : Command<Event>

data class FindEventByIdCommand(
    val id: Long
) : Command<Event>

data class FindEventsCommand(
    val pageable: Pageable,
    val filter: EventFilter
) : Command<Page<Event>>

data class DeleteEventByIdCommand(
    val eventId: Long
) : Command<Unit>

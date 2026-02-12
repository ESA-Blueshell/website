package net.blueshell.api.domain.event.application.command

import net.blueshell.api.domain.event.application.EventService
import net.blueshell.api.domain.event.command.ApproveEventCommand
import net.blueshell.api.domain.event.command.CreateEventCommand
import net.blueshell.api.domain.event.command.DeleteEventByIdCommand
import net.blueshell.api.domain.event.command.FindEventByIdCommand
import net.blueshell.api.domain.event.command.FindEventsCommand
import net.blueshell.api.domain.event.command.UpdateEventCommand
import net.blueshell.api.domain.event.persistence.Event
import net.blueshell.api.domain.event.web.mapping.asEntity
import net.blueshell.api.shared.command.CommandHandler
import org.springframework.data.domain.Page
import org.springframework.stereotype.Component

@Component
class CreateEventHandler(
    private val service: EventService
) : CommandHandler<CreateEventCommand, Event> {
    override val commandType = CreateEventCommand::class

    override fun handle(command: CreateEventCommand): Event {
        var event = command.dto.asEntity()
        event = service.create(event)
        return event
    }
}

@Component
class UpdateEventHandler(
    private val service: EventService
) : CommandHandler<UpdateEventCommand, Event> {
    override val commandType = UpdateEventCommand::class

    override fun handle(command: UpdateEventCommand): Event {
        var event = service.findById(command.id)
        command.dto.asEntity(event)
        event = service.update(event)
        return event
    }
}

@Component
class ApproveEventHandler(
    private val service: EventService
) : CommandHandler<ApproveEventCommand, Event> {
    override val commandType = ApproveEventCommand::class

    override fun handle(command: ApproveEventCommand): Event {
        var event = service.findById(command.id)
        event.approved = command.approved
        event = service.update(event)
        return event
    }
}

@Component
class FindEventByIdHandler(
    private val service: EventService
) : CommandHandler<FindEventByIdCommand, Event> {
    override val commandType = FindEventByIdCommand::class

    override fun handle(command: FindEventByIdCommand): Event {
        return service.findById(command.id)
    }
}

@Component
class FindEventsHandler(
    private val service: EventService
) : CommandHandler<FindEventsCommand, Page<Event>> {
    override val commandType = FindEventsCommand::class

    override fun handle(command: FindEventsCommand): Page<Event> {
        return service.findByFilter(command.pageable, command.filter)
    }
}

@Component
class DeleteEventByIdHandler(
    private val service: EventService
) : CommandHandler<DeleteEventByIdCommand, Unit> {
    override val commandType = DeleteEventByIdCommand::class

    override fun handle(command: DeleteEventByIdCommand) {
        service.deleteById(command.eventId)
    }
}

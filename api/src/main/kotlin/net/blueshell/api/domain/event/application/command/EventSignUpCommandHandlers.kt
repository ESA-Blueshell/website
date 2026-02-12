package net.blueshell.api.domain.event.application.command

import net.blueshell.api.domain.event.application.EventSignUpService
import net.blueshell.api.domain.event.command.CreateEventSignUpCommand
import net.blueshell.api.domain.event.command.DeleteEventSignUpCommand
import net.blueshell.api.domain.event.command.FindEventSignUpsByAccessTokenCommand
import net.blueshell.api.domain.event.command.FindEventSignUpsByEventIdCommand
import net.blueshell.api.domain.event.command.FindEventSignUpsCommand
import net.blueshell.api.domain.event.command.UpdateEventSignUpCommand
import net.blueshell.api.domain.event.persistence.EventSignUp
import net.blueshell.api.domain.event.web.mapping.asEntity
import net.blueshell.api.shared.command.CommandHandler
import org.springframework.stereotype.Component

@Component
class FindEventSignUpsHandler(
    private val service: EventSignUpService
) : CommandHandler<FindEventSignUpsCommand, MutableList<EventSignUp>> {
    override val commandType = FindEventSignUpsCommand::class

    override fun handle(command: FindEventSignUpsCommand): MutableList<EventSignUp> {
        return service.findByFilter(command.filter)
    }
}

@Component
class FindEventSignUpsByAccessTokenHandler(
    private val service: EventSignUpService
) : CommandHandler<FindEventSignUpsByAccessTokenCommand, MutableList<EventSignUp>> {
    override val commandType = FindEventSignUpsByAccessTokenCommand::class

    override fun handle(command: FindEventSignUpsByAccessTokenCommand): MutableList<EventSignUp> {
        return service.findByGuestAccessToken(command.accessToken)
    }
}

@Component
class FindEventSignUpsByEventIdHandler(
    private val service: EventSignUpService
) : CommandHandler<FindEventSignUpsByEventIdCommand, MutableList<EventSignUp>> {
    override val commandType = FindEventSignUpsByEventIdCommand::class

    override fun handle(command: FindEventSignUpsByEventIdCommand): MutableList<EventSignUp> {
        return service.findByEventId(command.eventId)
    }
}

@Component
class CreateEventSignUpHandler(
    private val service: EventSignUpService
) : CommandHandler<CreateEventSignUpCommand, EventSignUp> {
    override val commandType = CreateEventSignUpCommand::class

    override fun handle(command: CreateEventSignUpCommand): EventSignUp {
        command.principalId?.let { command.dto.userId = it }
        var eventSignUp = command.dto.asEntity()
        eventSignUp = service.create(eventSignUp)
        return eventSignUp
    }
}

@Component
class UpdateEventSignUpHandler(
    private val service: EventSignUpService
) : CommandHandler<UpdateEventSignUpCommand, EventSignUp> {
    override val commandType = UpdateEventSignUpCommand::class

    override fun handle(command: UpdateEventSignUpCommand): EventSignUp {
        val signUp = if (command.accessToken == null) {
            val principalId = requireNotNull(command.principalId) { "User must be authenticated" }
            service.findByUserIdAndEventId(principalId, command.eventId)
        } else {
            service.findByGuestAccessTokenAndEventId(command.accessToken, command.eventId)
        }
        command.dto.asEntity(signUp)
        return service.update(signUp)
    }
}

@Component
class DeleteEventSignUpHandler(
    private val service: EventSignUpService
) : CommandHandler<DeleteEventSignUpCommand, Unit> {
    override val commandType = DeleteEventSignUpCommand::class

    override fun handle(command: DeleteEventSignUpCommand) {
        service.deleteById(command.eventSignUpId)
    }
}

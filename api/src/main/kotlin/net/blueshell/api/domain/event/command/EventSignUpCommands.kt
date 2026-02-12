package net.blueshell.api.domain.event.command

import net.blueshell.api.domain.event.persistence.EventSignUp
import net.blueshell.api.domain.event.persistence.filter.EventSignUpFilter
import net.blueshell.api.domain.event.web.dto.EventSignUpDTO
import net.blueshell.api.shared.command.Command

data class FindEventSignUpsCommand(
    val filter: EventSignUpFilter
) : Command<MutableList<EventSignUp>>

data class FindEventSignUpsByAccessTokenCommand(
    val accessToken: String
) : Command<MutableList<EventSignUp>>

data class FindEventSignUpsByEventIdCommand(
    val eventId: Long
) : Command<MutableList<EventSignUp>>

data class CreateEventSignUpCommand(
    val dto: EventSignUpDTO,
    val principalId: Long?
) : Command<EventSignUp>

data class UpdateEventSignUpCommand(
    val eventId: Long,
    val dto: EventSignUpDTO,
    val accessToken: String?,
    val principalId: Long?
) : Command<EventSignUp>

data class DeleteEventSignUpCommand(
    val eventSignUpId: Long
) : Command<Unit>

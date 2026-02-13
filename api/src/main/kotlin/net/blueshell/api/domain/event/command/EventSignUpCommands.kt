package net.blueshell.api.domain.event.command

import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import net.blueshell.api.domain.event.application.validation.EventSignUpCandidate
import net.blueshell.api.domain.event.application.validation.ValidEventSignUpCommand
import net.blueshell.api.domain.event.persistence.EventSignUp
import net.blueshell.api.domain.event.application.query.EventSignUpQuery
import net.blueshell.api.domain.event.web.dto.EventSignUpDTO
import net.blueshell.api.shared.command.Command

data class FindEventSignUpsCommand(
    @field:NotNull(message = "Filter is required")
    var filter: EventSignUpQuery
) : Command<MutableList<EventSignUp>>

data class FindEventSignUpsByAccessTokenCommand(
    @field:NotBlank(message = "Access token is required")
    var accessToken: String
) : Command<MutableList<EventSignUp>>

data class FindEventSignUpsByEventIdCommand(
    @field:NotNull(message = "Event ID is required")
    var eventId: Long
) : Command<MutableList<EventSignUp>>

@ValidEventSignUpCommand
data class CreateEventSignUpCommand(
    @field:Valid
    @field:NotNull(message = "EventSignUp DTO is required")
    override var dto: EventSignUpDTO,
    val principalId: Long?
) : Command<EventSignUp>, EventSignUpCandidate

@ValidEventSignUpCommand
data class UpdateEventSignUpCommand(
    @field:NotNull(message = "Event ID is required")
    var eventId: Long,

    @field:Valid
    @field:NotNull(message = "EventSignUp DTO is required")
    override var dto: EventSignUpDTO,

    val accessToken: String?,
    val principalId: Long?
) : Command<EventSignUp>, EventSignUpCandidate

data class DeleteEventSignUpCommand(
    @field:NotNull(message = "EventSignUp ID is required")
    var eventSignUpId: Long
) : Command<Unit>

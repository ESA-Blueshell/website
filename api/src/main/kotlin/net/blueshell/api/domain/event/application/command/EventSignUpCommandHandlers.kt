package net.blueshell.api.domain.event.application.command

import net.blueshell.api.domain.event.application.EventSignUpService
import net.blueshell.api.domain.event.command.*
import net.blueshell.api.domain.event.persistence.EventSignUp
import net.blueshell.api.domain.event.persistence.Guest
import net.blueshell.api.domain.survey.persistence.Answer
import net.blueshell.api.domain.survey.persistence.Question
import net.blueshell.api.domain.event.web.dto.EventSignUpDTO
import net.blueshell.api.domain.event.web.dto.GuestDTO
import net.blueshell.api.domain.survey.web.dto.AnswerDTO
import net.blueshell.api.shared.util.MappingUtil.randomCapitalString
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
        var eventSignUp = mapSignUp(command.dto)
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
        applySignUp(command.dto, signUp)
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

private fun mapSignUp(dto: EventSignUpDTO): EventSignUp {
    val signUp = EventSignUp()
    applySignUp(dto, signUp)
    return signUp
}

private fun applySignUp(dto: EventSignUpDTO, signUp: EventSignUp) {
    signUp.event = net.blueshell.api.domain.event.persistence.Event::class.asRef(dto.eventId!!)
    dto.userId?.let { signUp.userId = it }
    signUp.guest = dto.guest?.let { mapGuest(it) }

    val mappedAnswers = dto.answers?.map { mapAnswer(it) } ?: emptyList()
    val answersSet = signUp.answers as MutableSet
    answersSet.clear()
    answersSet.addAll(mappedAnswers)

    dto.version?.let { signUp.version = it }
}

private fun mapGuest(dto: GuestDTO): Guest {
    val guest = Guest()
    guest.name = dto.name!!
    guest.discord = requireNotNull(dto.discord)
    guest.email = requireNotNull(dto.email)
    guest.phoneNumber = dto.phoneNumber
    dto.version?.let { guest.version = it }

    if (guest.accessToken == null) {
        guest.accessToken = randomCapitalString(30)
    }
    return guest
}

private fun mapAnswer(dto: AnswerDTO): Answer {
    val answer = Answer()
    answer.question = Question::class.asRef(dto.questionId!!)
    answer.optionSelections = dto.optionSelections
    answer.textResponse = dto.textResponse
    dto.version?.let { answer.version = it }
    return answer
}

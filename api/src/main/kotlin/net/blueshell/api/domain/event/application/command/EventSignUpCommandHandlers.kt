package net.blueshell.api.domain.event.application.command

import net.blueshell.api.domain.event.application.EventSignUpService
import net.blueshell.api.domain.event.command.*
import net.blueshell.api.domain.event.persistence.EventSignUp
import net.blueshell.api.domain.event.persistence.Guest
import net.blueshell.api.domain.event.persistence.repository.EventRepository
import net.blueshell.api.domain.survey.application.QuestionService
import net.blueshell.api.domain.survey.command.AnswerData
import net.blueshell.api.domain.survey.persistence.Answer
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
    private val service: EventSignUpService,
    private val eventRepository: EventRepository,
    private val questionService: QuestionService
) : CommandHandler<CreateEventSignUpCommand, EventSignUp> {
    override val commandType = CreateEventSignUpCommand::class

    override fun handle(command: CreateEventSignUpCommand): EventSignUp {
        // Apply principalId if provided (overrides data.userId)
        val signUpData = command.principalId?.let {
            command.data.copy(userId = it)
        } ?: command.data

        var eventSignUp = mapSignUp(signUpData, eventRepository, questionService)
        eventSignUp = service.create(eventSignUp)
        return eventSignUp
    }
}

@Component
class UpdateEventSignUpHandler(
    private val service: EventSignUpService,
    private val eventRepository: EventRepository,
    private val questionService: QuestionService
) : CommandHandler<UpdateEventSignUpCommand, EventSignUp> {
    override val commandType = UpdateEventSignUpCommand::class

    override fun handle(command: UpdateEventSignUpCommand): EventSignUp {
        val signUp = if (command.accessToken == null) {
            val principalId = requireNotNull(command.principalId) { "User must be authenticated" }
            service.findByUserIdAndEventId(principalId, command.eventId)
        } else {
            service.findByGuestAccessTokenAndEventId(command.accessToken, command.eventId)
        }
        applySignUp(command.data, signUp, eventRepository, questionService)
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

private fun mapSignUp(data: EventSignUpData, eventRepository: EventRepository, questionService: QuestionService): EventSignUp {
    val signUp = EventSignUp()
    applySignUp(data, signUp, eventRepository, questionService)
    return signUp
}

private fun applySignUp(data: EventSignUpData, signUp: EventSignUp, eventRepository: EventRepository, questionService: QuestionService) {
    signUp.event = eventRepository.getReferenceById(data.eventId)
    data.userId?.let { signUp.userId = it }
    signUp.guest = data.guest?.let { mapGuest(it) }

    val mappedAnswers = data.answers.map { mapAnswer(it, questionService) }
    val answersSet = signUp.answers as MutableSet
    answersSet.clear()
    answersSet.addAll(mappedAnswers)

    data.version?.let { signUp.version = it }
}

private fun mapGuest(data: GuestData): Guest {
    val guest = Guest()
    guest.name = data.name
    guest.discord = data.discord
    guest.email = data.email
    guest.phoneNumber = data.phoneNumber
    data.version?.let { guest.version = it }

    // Generate access token if not provided or if guest is new
    if (data.accessToken == null && guest.accessToken == null) {
        guest.accessToken = randomCapitalString(30)
    } else if (data.accessToken != null) {
        guest.accessToken = data.accessToken
    }
    return guest
}

private fun mapAnswer(data: AnswerData, questionService: QuestionService): Answer {
    val answer = Answer()
    answer.question = questionService.getReferenceById(data.questionId)
    answer.optionSelections = data.optionSelections?.toMutableList()
    answer.textResponse = data.textResponse
    data.version?.let { answer.version = it }
    return answer
}

package net.blueshell.api.domain.event.application.command

import net.blueshell.api.domain.event.application.EventSignUpService
import net.blueshell.api.domain.event.application.GuestService
import net.blueshell.api.domain.event.command.*
import net.blueshell.api.domain.event.persistence.EventSignUp
import net.blueshell.api.domain.event.persistence.Guest
import net.blueshell.api.domain.event.persistence.GuestAccessTokenCodec
import net.blueshell.api.domain.event.persistence.repository.EventRepository
import net.blueshell.api.domain.survey.application.QuestionService
import net.blueshell.api.domain.survey.command.AnswerData
import net.blueshell.api.domain.survey.persistence.Answer
import net.blueshell.api.shared.command.CommandHandler
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.server.ResponseStatusException

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
        val signUpData = if (command.principalId != null) {
            // Authenticated users can only act as themselves.
            command.data.copy(userId = command.principalId)
        } else {
            // Anonymous signups must always be guest signups.
            if (command.data.guest == null) {
                throw ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Guest details are required for anonymous sign-ups."
                )
            }
            command.data.copy(userId = null)
        }

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
        val signUp: EventSignUp
        val signUpData: EventSignUpData
        if (command.accessToken == null) {
            val principalId = requireNotNull(command.principalId) { "User must be authenticated" }
            signUp = service.findByUserIdAndEventId(principalId, command.eventId)
            // Authenticated users can only update their own signup.
            signUpData = command.data.copy(userId = principalId)
        } else {
            signUp = service.findByGuestAccessTokenAndEventId(command.accessToken, command.eventId)
            // Guest token flow cannot assign a user id.
            signUpData = command.data.copy(userId = null)
        }
        applySignUp(signUpData, signUp, eventRepository, questionService)
        return service.update(signUp)
    }
}

@Component
class DeleteEventSignUpHandler(
    private val service: EventSignUpService,
    private val guestService: GuestService
) : CommandHandler<DeleteEventSignUpCommand, Unit> {
    override val commandType = DeleteEventSignUpCommand::class

    override fun handle(command: DeleteEventSignUpCommand) {
        val accessToken = command.accessToken
        if (accessToken.isNullOrBlank()) {
            service.deleteById(command.eventSignUpId)
            return
        }

        // Preserve 404 semantics for unknown guest tokens before target-signup binding check.
        guestService.findByAccessToken(accessToken)
        val signUp = service.findById(command.eventSignUpId)
        if (signUp.guest?.matchesAccessToken(accessToken) != true) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "Guest token does not match signup")
        }

        service.delete(signUp)
    }
}

private fun mapSignUp(data: EventSignUpData, eventRepository: EventRepository, questionService: QuestionService): EventSignUp {
    val signUp = EventSignUp(event = eventRepository.getReferenceById(data.eventId))
    applySignUp(data, signUp, eventRepository, questionService)
    return signUp
}

private fun applySignUp(
    data: EventSignUpData,
    signUp: EventSignUp,
    eventRepository: EventRepository,
    questionService: QuestionService,
) {
    signUp.event = eventRepository.getReferenceById(data.eventId)
    signUp.userId = data.userId
    applyGuest(data.guest, signUp)

    val mappedAnswers = data.answers.map { mapAnswer(it, questionService) }
    val answersSet = signUp.answers as MutableSet
    answersSet.clear()
    answersSet.addAll(mappedAnswers)

    data.version?.let { signUp.version = it }
}

private fun applyGuest(data: GuestData?, signUp: EventSignUp) {
    if (data == null) {
        signUp.guest = null
        return
    }

    val existing = signUp.guest
    if (existing == null) {
        signUp.guest = mapGuest(data)
        return
    }

    existing.name = data.name
    existing.discord = data.discord
    existing.email = data.email
    existing.phoneNumber = data.phoneNumber
    data.version?.let { existing.version = it }
}

private fun mapGuest(data: GuestData): Guest {
    val rawAccessToken = data.accessToken ?: GuestAccessTokenCodec.generate()
    val guest = Guest.withRawToken(
        name = data.name,
        discord = data.discord,
        email = data.email,
        phoneNumber = data.phoneNumber,
        accessToken = rawAccessToken,
    )
    data.version?.let { guest.version = it }
    return guest
}

private fun mapAnswer(data: AnswerData, questionService: QuestionService): Answer {
    val answer = Answer(
        question = questionService.getReferenceById(data.questionId),
        optionSelections = data.optionSelections?.toMutableList(),
        textResponse = data.textResponse,
    )
    data.version?.let { answer.version = it }
    return answer
}

package net.blueshell.api.domain.event.application.command

import net.blueshell.api.domain.event.application.EventService
import net.blueshell.api.domain.event.application.EventSignUpService
import net.blueshell.api.domain.event.command.*
import net.blueshell.api.domain.event.persistence.EventSignUp
import net.blueshell.api.domain.event.web.mapping.asEntity
import net.blueshell.api.domain.survey.persistence.Question
import net.blueshell.api.shared.enums.QuestionType
import net.blueshell.api.shared.command.CommandHandler
import net.blueshell.api.shared.validation.DatabaseValidationErrors
import org.springframework.stereotype.Component
import java.util.LinkedHashSet

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
    private val events: EventService
) : CommandHandler<CreateEventSignUpCommand, EventSignUp> {
    override val commandType = CreateEventSignUpCommand::class

    override fun handle(command: CreateEventSignUpCommand): EventSignUp {
        validateEventSignUp(
            CreateEventSignUpCommand::class.simpleName ?: "CreateEventSignUpCommand",
            command.dto,
            events
        )
        command.principalId?.let { command.dto.userId = it }
        var eventSignUp = command.dto.asEntity()
        eventSignUp = service.create(eventSignUp)
        return eventSignUp
    }
}

@Component
class UpdateEventSignUpHandler(
    private val service: EventSignUpService,
    private val events: EventService
) : CommandHandler<UpdateEventSignUpCommand, EventSignUp> {
    override val commandType = UpdateEventSignUpCommand::class

    override fun handle(command: UpdateEventSignUpCommand): EventSignUp {
        validateEventSignUp(
            UpdateEventSignUpCommand::class.simpleName ?: "UpdateEventSignUpCommand",
            command.dto,
            events
        )
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

private fun validateEventSignUp(objectName: String, dto: net.blueshell.api.domain.event.web.dto.EventSignUpDTO, events: EventService) {
    val errors = DatabaseValidationErrors(objectName)
    val eventId = dto.eventId
    if (eventId == null) {
        errors.reject("eventId", null, "Event id is required.", "NotNull")
        errors.throwIfAny()
        return
    }

    val event = try {
        events.findById(eventId)
    } catch (ex: Exception) {
        errors.reject("eventId", eventId, "Unknown event.", "ValidEventSignUp")
        errors.throwIfAny()
        return
    }

    val form = event.signUpForm
    val questions = form?.questions ?: emptySet()
    val formQuestionIds = questions
        .filter { q: Question -> q.type != QuestionType.DESCRIPTION }
        .mapNotNull { it.id }
        .toCollection(LinkedHashSet())

    if (formQuestionIds.isEmpty()) {
        errors.throwIfAny()
        return
    }

    val answers = dto.answers ?: mutableListOf()
    val provided = LinkedHashSet<Long>()

    answers.forEachIndexed { index, answer ->
        val questionId = answer.questionId
        if (questionId == null) {
            errors.reject("answers[$index].questionId", null, "Question id is required.", "NotNull")
            return@forEachIndexed
        }

        if (!formQuestionIds.contains(questionId)) {
            errors.reject(
                "answers[$index].questionId",
                questionId,
                "Question does not belong to this event's sign-up form (id=$questionId).",
                "ValidEventSignUp"
            )
        }

        if (!provided.add(questionId)) {
            errors.reject(
                "answers[$index].questionId",
                questionId,
                "Duplicate answer for questionId $questionId.",
                "ValidEventSignUp"
            )
        }
    }

    val missing = LinkedHashSet(formQuestionIds)
    missing.removeAll(provided)
    if (missing.isNotEmpty()) {
        errors.reject("answers", answers, "Missing answers for questionIds: $missing", "ValidEventSignUp")
    }

    errors.throwIfAny()
}

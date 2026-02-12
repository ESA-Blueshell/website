package net.blueshell.api.domain.event.application.command

import net.blueshell.api.domain.event.application.EventService
import net.blueshell.api.domain.event.command.*
import net.blueshell.api.domain.event.persistence.Event
import net.blueshell.api.domain.committee.persistence.Committee
import net.blueshell.api.domain.event.persistence.EventBanner
import net.blueshell.api.domain.survey.persistence.Question
import net.blueshell.api.domain.survey.persistence.Survey
import net.blueshell.api.shared.enums.Role
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import net.blueshell.api.shared.command.CommandHandler
import org.springframework.data.domain.Page
import org.springframework.stereotype.Component

@Component
class CreateEventHandler(
    private val service: EventService
) : CommandHandler<CreateEventCommand, Event> {
    override val commandType = CreateEventCommand::class

    override fun handle(command: CreateEventCommand): Event {
        var event = Event()
        applyEventFields(event, command)
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
        applyEventFields(event, command)
        command.version?.let { event.version = it }
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

private fun applyEventFields(event: Event, command: CreateEventCommand) {
    event.committee = Committee::class.asRef(command.committeeId)
    event.title = command.title
    event.description = command.description
    event.location = command.location
    event.startTime = command.startTime
    event.endTime = command.endTime
    event.memberPrice = command.memberPrice
    event.publicPrice = command.publicPrice
    event.membersOnly = command.membersOnly
    event.signUp = command.signUp
    event.banner = command.banner?.let { mapBanner(it) }
    event.signUpForm = command.signUpForm?.let { mapSurvey(it) }
    event.approved = hasAuthority(Role.BOARD) && command.approved
}

private fun applyEventFields(event: Event, command: UpdateEventCommand) {
    event.committee = Committee::class.asRef(command.committeeId)
    event.title = command.title
    event.description = command.description
    event.location = command.location
    event.startTime = command.startTime
    event.endTime = command.endTime
    event.memberPrice = command.memberPrice
    event.publicPrice = command.publicPrice
    event.membersOnly = command.membersOnly
    event.signUp = command.signUp
    event.banner = command.banner?.let { mapBanner(it) }
    event.signUpForm = command.signUpForm?.let { mapSurvey(it) }
    event.approved = hasAuthority(Role.BOARD) && command.approved
}

private fun mapBanner(request: net.blueshell.api.domain.event.web.dto.EventBannerRequest): EventBanner {
    val banner = EventBanner()
    banner.id.fileId = request.fileId
    request.version?.let { banner.version = it }
    return banner
}

private fun mapSurvey(request: net.blueshell.api.domain.survey.web.dto.SurveyRequest): Survey {
    val survey = Survey()
    val questions = request.questions?.map { qRequest ->
        val question = Question()
        question.idx = qRequest.idx!!
        question.type = qRequest.type!!
        question.label = qRequest.label!!
        question.choiceLabels = qRequest.choiceLabels
        question.survey = survey
        question
    } ?: emptyList()
    survey.replaceQuestions(questions)
    return survey
}

private fun hasAuthority(role: Role): Boolean {
    val authentication = SecurityContextHolder.getContext().authentication
    return authentication != null && authentication.authorities.any { a: GrantedAuthority? ->
        a?.authority == role.toString()
    }
}

package net.blueshell.api.domain.event.application.command

import net.blueshell.api.domain.event.application.EventService
import net.blueshell.api.domain.event.command.*
import net.blueshell.api.domain.event.persistence.Event
import net.blueshell.api.domain.committee.application.CommitteeService
import net.blueshell.api.domain.committee.persistence.Committee
import net.blueshell.api.domain.event.persistence.EventBanner
import net.blueshell.api.domain.survey.application.factory.SurveyFactory
import net.blueshell.api.domain.survey.persistence.Question
import net.blueshell.api.domain.survey.persistence.Survey
import net.blueshell.api.shared.enums.Role
import net.blueshell.api.shared.command.CommandHandler
import net.blueshell.api.shared.security.CurrentUser
import net.blueshell.api.shared.security.CurrentUserProvider
import org.springframework.data.domain.Page
import org.springframework.stereotype.Component

@Component
class CreateEventHandler(
    private val service: EventService,
    private val committeeService: CommitteeService,
    private val currentUserProvider: CurrentUserProvider,
    private val surveyFactory: SurveyFactory
) : CommandHandler<CreateEventCommand, Event> {
    override val commandType = CreateEventCommand::class

    override fun handle(command: CreateEventCommand): Event {
        var event = Event()
        val isBoard = currentUserProvider.currentUser()?.let { hasAuthority(it, Role.BOARD) } == true
        applyEventFields(event, command, isBoard, committeeService, surveyFactory)
        event = service.create(event)
        return event
    }
}

@Component
class UpdateEventHandler(
    private val service: EventService,
    private val committeeService: CommitteeService,
    private val currentUserProvider: CurrentUserProvider,
    private val surveyFactory: SurveyFactory
) : CommandHandler<UpdateEventCommand, Event> {
    override val commandType = UpdateEventCommand::class

    override fun handle(command: UpdateEventCommand): Event {
        var event = service.findById(command.id)
        val isBoard = currentUserProvider.currentUser()?.let { hasAuthority(it, Role.BOARD) } == true
        applyEventFields(event, command, isBoard, committeeService, surveyFactory)
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

private fun applyEventFields(event: Event, command: CreateEventCommand, isBoard: Boolean, committeeService: CommitteeService, surveyFactory: SurveyFactory) {
    event.committee = committeeService.findById(command.committeeId)
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
    event.signUpForm = command.signUpForm?.let { surveyFactory.createFromData(it) }
    event.approved = isBoard && command.approved
}

private fun applyEventFields(event: Event, command: UpdateEventCommand, isBoard: Boolean, committeeService: CommitteeService, surveyFactory: SurveyFactory) {
    event.committee = committeeService.findById(command.committeeId)
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
    event.signUpForm = command.signUpForm?.let { surveyFactory.createFromData(it) }
    event.approved = isBoard && command.approved
}

private fun mapBanner(data: EventBannerData): EventBanner {
    val banner = EventBanner()
    banner.id.fileId = data.fileId
    return banner
}

private fun hasAuthority(user: CurrentUser, role: Role): Boolean {
    val inherited = user.roles.flatMap { it.allInheritedRoles }
    return inherited.any { it.matchesRole(role) }
}

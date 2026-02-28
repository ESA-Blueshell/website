package net.blueshell.api.domain.event.application.command

import net.blueshell.api.domain.committee.application.CommitteeService
import net.blueshell.api.domain.committee.persistence.Committee
import net.blueshell.api.domain.event.application.EventService
import net.blueshell.api.domain.event.command.*
import net.blueshell.api.domain.event.persistence.Event
import net.blueshell.api.domain.event.persistence.EventBanner
import net.blueshell.api.domain.file.application.FileService
import net.blueshell.api.domain.survey.application.factory.SurveyFactory
import net.blueshell.api.domain.survey.command.SurveyData
import net.blueshell.api.domain.survey.persistence.Question
import net.blueshell.api.shared.command.CommandHandler
import net.blueshell.api.shared.enums.Role
import net.blueshell.api.shared.security.CurrentUser
import net.blueshell.api.shared.security.CurrentUserProvider
import org.springframework.data.domain.Page
import org.springframework.stereotype.Component

@Component
class CreateEventHandler(
    private val service: EventService,
    private val committeeService: CommitteeService,
    private val currentUserProvider: CurrentUserProvider,
    private val surveyFactory: SurveyFactory,
    private val fileService: FileService,
) : CommandHandler<CreateEventCommand, Event> {
    override val commandType = CreateEventCommand::class

    override fun handle(command: CreateEventCommand): Event {
        val isBoard = currentUserProvider.currentUser()?.let { hasAuthority(it, Role.BOARD) } == true
        val event = Event(
            committee = committeeService.findById(command.committeeId),
            title = command.title,
            description = command.description,
            location = command.location,
            startTime = command.startTime,
            endTime = command.endTime,
            memberPrice = command.memberPrice,
            publicPrice = command.publicPrice,
            approved = isBoard && command.approved,
            membersOnly = command.membersOnly,
            signUp = command.signUp,
        )
        event.replaceBanner(command.banner?.toEntity(event, fileService))
        event.replaceSignUpForm(command.signUpForm?.let(surveyFactory::createFromData))
        return service.create(event)
    }
}

@Component
class UpdateEventHandler(
    private val service: EventService,
    private val committeeService: CommitteeService,
    private val currentUserProvider: CurrentUserProvider,
    private val surveyFactory: SurveyFactory,
    private val fileService: FileService,
) : CommandHandler<UpdateEventCommand, Event> {
    override val commandType = UpdateEventCommand::class

    override fun handle(command: UpdateEventCommand): Event {
        val event = service.findById(command.id)
        val isBoard = currentUserProvider.currentUser()?.let { hasAuthority(it, Role.BOARD) } == true
        event.applyEditableFields(command, committeeService.findById(command.committeeId))
        event.replaceBanner(command.banner?.toEntity(event, fileService, existingBanner = event.banner))
        applySignUpFormUpdate(event, command.signUpForm, surveyFactory)
        event.approved = isBoard && command.approved
        event.version = command.version
        return service.update(event)
    }
}

@Component
class ApproveEventHandler(
    private val service: EventService
) : CommandHandler<ApproveEventCommand, Event> {
    override val commandType = ApproveEventCommand::class

    override fun handle(command: ApproveEventCommand): Event {
        val event = service.findById(command.id)
        event.approved = command.approved
        return service.update(event)
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

private fun hasAuthority(user: CurrentUser, role: Role): Boolean {
    val inherited = user.roles.flatMap { it.allInheritedRoles }
    return inherited.any { it.matchesRole(role) }
}

private fun EventBannerData.toEntity(
    event: Event,
    fileService: FileService,
    existingBanner: EventBanner? = null
): EventBanner {
    if (existingBanner != null && existingBanner.fileId == fileId) {
        return existingBanner  // Same file — reuse managed entity, no INSERT needed
    }
    return EventBanner(
        event = event,
        file = fileService.findById(fileId),
    )
}

private fun Event.applyEditableFields(command: UpdateEventCommand, committee: Committee) {
    this.committee = committee
    this.title = command.title
    this.description = command.description
    this.location = command.location
    this.startTime = command.startTime
    this.endTime = command.endTime
    this.memberPrice = command.memberPrice
    this.publicPrice = command.publicPrice
    this.membersOnly = command.membersOnly
    this.signUp = command.signUp
}

private fun applySignUpFormUpdate(
    event: Event,
    signUpFormData: SurveyData?,
    surveyFactory: SurveyFactory
) {
    if (signUpFormData == null) {
        event.replaceSignUpForm(null)
        return
    }

    val existingSurvey = event.signUpForm
    if (existingSurvey == null) {
        event.replaceSignUpForm(surveyFactory.createFromData(signUpFormData))
        return
    }

    val questionsByIdx = existingSurvey.questions.associateBy { it.idx }
    val mappedQuestions = signUpFormData.questions.map { incoming ->
        questionsByIdx[incoming.idx]?.apply {
            type = incoming.type
            label = incoming.label
            choiceLabels = incoming.choiceLabels?.toMutableList()
        } ?: Question(
            idx = incoming.idx,
            survey = existingSurvey,
            type = incoming.type,
            label = incoming.label,
            choiceLabels = incoming.choiceLabels?.toMutableList(),
        )
    }
    existingSurvey.replaceQuestions(mappedQuestions)
    event.replaceSignUpForm(existingSurvey)
}

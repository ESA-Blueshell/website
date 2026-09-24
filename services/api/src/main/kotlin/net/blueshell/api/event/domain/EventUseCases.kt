package net.blueshell.api.event.domain

import net.blueshell.api.committee.api.CommitteeService
import net.blueshell.api.committee.persistence.Committee
import net.blueshell.api.event.api.EventService
import net.blueshell.api.event.persistence.Event
import net.blueshell.api.event.persistence.PingedRole
import net.blueshell.api.event.persistence.EventBanner
import net.blueshell.api.file.api.FileService
import net.blueshell.api.game.api.GameService
import net.blueshell.api.shared.enums.Role
import net.blueshell.api.shared.security.CurrentUser
import net.blueshell.api.shared.security.CurrentUserProvider
import net.blueshell.api.survey.api.SurveyData
import net.blueshell.api.survey.api.SurveyFactory
import net.blueshell.api.survey.persistence.Question
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service

/**
 * Event writes that resolve a committee, a banner and a sign-up form before
 * persisting. Reads and deletes go straight to [EventService].
 */
@Service
class EventUseCases(
    private val service: EventService,
    private val committeeService: CommitteeService,
    private val currentUserProvider: CurrentUserProvider,
    private val surveyFactory: SurveyFactory,
    private val fileService: FileService,
    private val games: GameService,
    @param:Value($$"${discord.guildId:}") private val discordGuildId: String = "",
) {
    /* @everyone's ID is the server's own, and pinging it reaches everybody, which a pinged role may not. */
    private fun refuseEveryone(data: EventData) {
        if (discordGuildId.isNotEmpty() && data.pingedRoles.orEmpty().any { it.id == discordGuildId }) {
            throw InvalidEventException("@everyone cannot be a pinged role")
        }
    }

    fun create(data: EventData): Event {
        refuseEveryone(data)
        val event =
            Event(
                committee = committeeService.findById(data.committeeId),
                title = data.title,
                description = data.description,
                location = data.location,
                startTime = data.startTime,
                endTime = data.endTime,
                memberPrice = data.memberPrice,
                publicPrice = data.publicPrice,
                // Only the board may publish directly; anyone else's request is held for approval.
                approved = isBoard() && data.approved,
                membersOnly = data.membersOnly,
                signUp = data.signUp,
                signUpDeadline = data.signUpDeadline,
                signUpLimit = data.signUpLimit,
            )
        event.replaceBanner(data.banner?.toEntity(event, fileService))
        event.replaceSignUpForm(data.signUpForm?.let(surveyFactory::createFromData))
        event.applyPingedRoles(data)
        applyGames(event, data)
        return service.create(event)
    }

    fun update(
        id: Long,
        data: EventData,
        removeExistingSignUps: Boolean,
        version: Long,
    ): Event {
        refuseEveryone(data)
        val event = service.findById(id)
        event.applyEditableFields(data, committeeService.findById(data.committeeId))
        applyGames(event, data)
        event.replaceBanner(data.banner?.toEntity(event, fileService, existingBanner = event.banner))
        applySignUpFormUpdate(event, data.signUpForm, surveyFactory)
        event.approved = isBoard() && data.approved
        event.version = version
        return service.update(event, removeExistingSignUps = removeExistingSignUps)
    }

    fun approve(
        id: Long,
        approved: Boolean,
    ): Event {
        val event = service.findById(id)
        event.approved = approved
        return service.update(event)
    }

    /* An archived game the event already names stays named; one cannot be newly picked. */
    private fun applyGames(
        event: Event,
        data: EventData,
    ) {
        val codes = data.gameCodes ?: return
        val named = games.requireNameable(codes, kept = event.gameCodes.toSet())
        event.gameCodes.clear()
        event.gameCodes.addAll(named)
    }

    private fun isBoard(): Boolean = currentUserProvider.currentUser()?.let { hasAuthority(it, Role.BOARD) } == true
}

private fun hasAuthority(
    user: CurrentUser,
    role: Role,
): Boolean {
    val inherited = user.roles.flatMap { it.allInheritedRoles }
    return inherited.any { it.matchesRole(role) }
}

private fun EventBannerData.toEntity(
    event: Event,
    fileService: FileService,
    existingBanner: EventBanner? = null,
): EventBanner {
    if (existingBanner != null && existingBanner.fileId == fileId) {
        return existingBanner // Same file — reuse managed entity, no INSERT needed
    }
    return EventBanner(
        event = event,
        file = fileService.findById(fileId),
    )
}

private fun Event.applyEditableFields(
    data: EventData,
    committee: Committee,
) {
    this.committee = committee
    this.title = data.title
    this.description = data.description
    this.location = data.location
    this.startTime = data.startTime
    this.endTime = data.endTime
    this.memberPrice = data.memberPrice
    this.publicPrice = data.publicPrice
    this.membersOnly = data.membersOnly
    this.signUp = data.signUp
    if (data.signUp == false) {
        this.signUpDeadline = null
        this.signUpLimit = null
    } else {
        this.signUpDeadline = data.signUpDeadline
        this.signUpLimit = data.signUpLimit
    }
    applyPingedRoles(data)
}

private fun Event.applyPingedRoles(data: EventData) {
    val roles = data.pingedRoles ?: return
    pingedRoles.clear()
    roles.mapTo(pingedRoles) { PingedRole(roleId = it.id, roleName = it.name) }
}

private fun applySignUpFormUpdate(
    event: Event,
    signUpFormData: SurveyData?,
    surveyFactory: SurveyFactory,
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
    val mappedQuestions =
        signUpFormData.questions.map { incoming ->
            questionsByIdx[incoming.idx]?.apply {
                type = incoming.type
                label = incoming.label
                choiceLabels = incoming.choiceLabels?.toMutableList()
                required = incoming.required
            } ?: Question(
                idx = incoming.idx,
                survey = existingSurvey,
                type = incoming.type,
                label = incoming.label,
                choiceLabels = incoming.choiceLabels?.toMutableList(),
                required = incoming.required,
            )
        }
    existingSurvey.replaceQuestions(mappedQuestions)
    event.replaceSignUpForm(existingSurvey)
}

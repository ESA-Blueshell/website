package net.blueshell.api.event.domain

import jakarta.validation.ConstraintViolationException
import jakarta.validation.Validator
import net.blueshell.api.event.persistence.EventRepository
import net.blueshell.api.event.persistence.EventSignUp
import net.blueshell.api.event.persistence.Guest
import net.blueshell.api.event.persistence.GuestAccessTokenCodec
import net.blueshell.api.shared.enums.Role
import net.blueshell.api.shared.job.EmailJobs
import net.blueshell.api.shared.job.JobQueue
import net.blueshell.api.shared.security.CurrentUserProvider
import net.blueshell.api.survey.api.AnswerData
import net.blueshell.api.survey.api.QuestionService
import net.blueshell.api.user.api.UserService
import net.blueshell.api.survey.persistence.Answer
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException

/**
 * Sign-up writes. Who the sign-up belongs to is decided here, never taken from
 * the payload: an authenticated caller can only act as themselves, and an
 * anonymous one must supply guest details.
 */
@Service
class EventSignUpUseCases(
    private val service: EventSignUpService,
    private val eventRepository: EventRepository,
    private val questionService: QuestionService,
    private val guestService: GuestService,
    private val validator: Validator,
    private val jobs: JobQueue,
    private val users: UserService,
    private val currentUser: CurrentUserProvider,
) {
    /** Board and above, asked of the caller rather than of the route they came in on. */
    private fun callerIsBoard(): Boolean =
        currentUser.currentUser()?.roles?.any { it.matchesRole(Role.BOARD) } == true
    /**
     * Applies the declarative rules on [EventSignUpData] by hand: the event id arrives on the
     * path rather than in a body, so there is no request DTO to carry the annotation.
     */
    private fun validate(data: EventSignUpData) {
        val violations = validator.validate(data)
        if (violations.isNotEmpty()) throw ConstraintViolationException(violations)
    }

    fun create(
        data: EventSignUpData,
        principalId: Long?,
    ): EventSignUp {
        val signUpData =
            if (principalId != null) {
                // Authenticated users can only act as themselves.
                data.copy(userId = principalId)
            } else {
                // Anonymous signups must always be guest signups.
                if (data.guest == null) {
                    throw ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Guest details are required for anonymous sign-ups.",
                    )
                }
                data.copy(userId = null)
            }
        validate(signUpData)
        return service.create(mapSignUp(signUpData, eventRepository, questionService))
    }

    fun update(
        eventId: Long,
        data: EventSignUpData,
        principalId: Long?,
        accessToken: String?,
    ): EventSignUp {
        val signUp: EventSignUp
        val signUpData: EventSignUpData
        if (accessToken == null) {
            val id = requireNotNull(principalId) { "User must be authenticated" }
            signUp = service.findByUserIdAndEventId(id, eventId)
            // Authenticated users can only update their own signup.
            signUpData = data.copy(userId = id)
        } else {
            signUp = service.findByGuestAccessTokenAndEventId(accessToken, eventId)
            // Guest token flow cannot assign a user id.
            signUpData = data.copy(userId = null)
        }
        validate(signUpData)
        applySignUp(signUpData, signUp, eventRepository, questionService)
        return service.update(signUp)
    }

    /**
     * A board-side update, addressed by the sign-up rather than by the caller. The event and the
     * holder stay as they are; only the answers and a guest's own details are rewritten.
     */
    fun updateById(
        eventSignUpId: Long,
        data: EventSignUpData,
    ): EventSignUp {
        val signUp = service.findById(eventSignUpId)
        // One direction only: a guest sign-up may move onto an account, never the other way.
        val movingTo = data.userId?.takeIf { signUp.userId == null && signUp.guest != null }
        val retiredGuest = movingTo?.let { checkReassignment(signUp, it) }

        val signUpData =
            data.copy(
                eventId = signUp.eventId,
                userId = movingTo ?: signUp.userId,
                // An account sign-up has no guest to edit, and a guest sign-up keeps the guest it
                // has when the body says nothing about it.
                guest =
                    when {
                        movingTo != null -> null
                        signUp.guest == null -> null
                        else -> data.guest ?: signUp.guest!!.asData()
                    },
                // The deadline and the limit bind an owner correcting their own answers, and do
                // not bind a board member correcting a roster after the fact.
                boardEdit = callerIsBoard(),
            )
        validate(signUpData)
        applySignUp(signUpData, signUp, eventRepository, questionService)
        val updated = service.update(signUp)
        retiredGuest?.let { guestService.delete(it) }
        return updated
    }

    /** Refuses a move the api would not allow, and hands back the guest the move retires. */
    private fun checkReassignment(
        signUp: EventSignUp,
        targetUserId: Long,
    ): Guest {
        val target = users.findById(targetUserId)
        if (signUp.event.membersOnly && !target.hasAuthority(Role.MEMBER)) {
            throw ResponseStatusException(
                HttpStatus.UNPROCESSABLE_CONTENT,
                "This event is members-only, and this person is not a member.",
            )
        }
        if (service.existsByUserIdAndEventId(targetUserId, signUp.eventId)) {
            throw ResponseStatusException(
                HttpStatus.CONFLICT,
                "This person already has a sign-up for this event.",
            )
        }
        return signUp.guest!!
    }

    /**
     * [notify] is the board's choice to tell the person, and is honoured for a board caller alone:
     * somebody cancelling their own sign-up would only be emailing themselves.
     */
    fun delete(
        eventSignUpId: Long,
        accessToken: String?,
        notify: Boolean = false,
    ) {
        if (accessToken.isNullOrBlank()) {
            if (!notify || !callerIsBoard()) {
                service.deleteById(eventSignUpId)
                return
            }
            // Read the recipient off the sign-up while it is still there to read.
            val signUp = service.findById(eventSignUpId)
            val removal = removalNotice(signUp)
            service.delete(signUp)
            removal?.let { jobs.runAsync(EmailJobs.EventSignUpRemoved, it) }
            return
        }
        // Preserve 404 semantics for unknown guest tokens before target-signup binding check.
        guestService.findByAccessToken(accessToken)
        val signUp = service.findById(eventSignUpId)
        if (signUp.guest?.matchesAccessToken(accessToken) != true) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "Guest token does not match signup")
        }
        service.delete(signUp)
    }
}

private fun Guest.asData(): GuestData =
    GuestData(
        name = this.name,
        email = this.email,
        discord = this.discord,
        phoneNumber = this.phoneNumber ?: "",
        version = this.version,
    )

private fun removalNotice(signUp: EventSignUp): EmailJobs.EventSignUpRemovedPayload? {
    val user = signUp.user
    val guest = signUp.guest
    val (email, name) =
        when {
            user != null -> user.email to user.fullName
            guest != null -> guest.email to guest.name
            else -> return null
        }
    return EmailJobs.EventSignUpRemovedPayload(
        recipientEmail = email,
        recipientName = name,
        eventTitle = signUp.event.title,
    )
}

private fun mapSignUp(
    data: EventSignUpData,
    eventRepository: EventRepository,
    questionService: QuestionService,
): EventSignUp {
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

private fun applyGuest(
    data: GuestData?,
    signUp: EventSignUp,
) {
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
    val guest =
        Guest.withRawToken(
            name = data.name,
            discord = data.discord,
            email = data.email,
            phoneNumber = data.phoneNumber,
            accessToken = rawAccessToken,
        )
    data.version?.let { guest.version = it }
    return guest
}

private fun mapAnswer(
    data: AnswerData,
    questionService: QuestionService,
): Answer {
    val answer =
        Answer(
            question = questionService.getReferenceById(data.questionId),
            optionSelections = data.optionSelections?.toMutableList(),
            textResponse = data.textResponse,
        )
    data.version?.let { answer.version = it }
    return answer
}

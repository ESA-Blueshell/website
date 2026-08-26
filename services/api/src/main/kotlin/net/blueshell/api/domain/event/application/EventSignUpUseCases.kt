package net.blueshell.api.domain.event.application

import net.blueshell.api.domain.event.application.GuestService
import net.blueshell.api.domain.event.persistence.EventSignUp
import net.blueshell.api.domain.event.persistence.Guest
import net.blueshell.api.domain.event.persistence.GuestAccessTokenCodec
import net.blueshell.api.domain.event.persistence.repository.EventRepository
import net.blueshell.api.domain.survey.application.QuestionService
import net.blueshell.api.domain.survey.application.AnswerData
import net.blueshell.api.domain.survey.persistence.Answer
import org.springframework.http.HttpStatus
import jakarta.validation.ConstraintViolationException
import jakarta.validation.Validator
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
) {
    /**
     * `@ValidEventSignUpCommand` used to sit on the two sign-up commands and was
     * applied by the dispatcher. With the commands gone there is no request DTO that
     * can carry it — the event id arrives on the path, not in the body — so the rule
     * stays declarative on [EventSignUpData] and is applied here instead. The
     * exception type is the one the dispatcher raised, so the response is unchanged.
     */
    private fun validate(data: EventSignUpData) {
        val violations = validator.validate(data)
        if (violations.isNotEmpty()) throw ConstraintViolationException(violations)
    }
    fun create(data: EventSignUpData, principalId: Long?): EventSignUp {
        val signUpData = if (principalId != null) {
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

    fun update(eventId: Long, data: EventSignUpData, principalId: Long?, accessToken: String?): EventSignUp {
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

    fun delete(eventSignUpId: Long, accessToken: String?) {
        if (accessToken.isNullOrBlank()) {
            service.deleteById(eventSignUpId)
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

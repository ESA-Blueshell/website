package net.blueshell.api.domain.event.application

import net.blueshell.api.domain.event.application.event.EventSignUpCreated
import net.blueshell.api.domain.event.persistence.EventSignUp
import net.blueshell.api.domain.event.persistence.GuestAccessTokenCodec
import net.blueshell.api.domain.event.application.query.EventSignUpQuery
import net.blueshell.api.domain.event.persistence.repository.EventSignUpRepository
import net.blueshell.api.domain.event.persistence.spec.EventSignUpSpecifications
import net.blueshell.api.shared.security.CurrentUserProvider
import net.blueshell.api.shared.event.TrackedEventPublisher
import net.blueshell.api.shared.service.BaseModelService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.util.function.Supplier

@Service
class EventSignUpService @Autowired constructor(
    repository: EventSignUpRepository,
    private val trackedEvents: TrackedEventPublisher,
    private val currentUserProvider: CurrentUserProvider
) : BaseModelService<EventSignUp, Long, EventSignUpRepository>(repository) {
    @Transactional
    override fun create(entity: EventSignUp): EventSignUp {
        val saved = super.create(entity)
        trackedEvents.publish { actor ->
            EventSignUpCreated(
                saved.id!!,
                guestAccessToken = saved.guest?.accessTokenRaw,
                actor = actor
            )
        }
        return saved
    }

    @Transactional
    override fun update(entity: EventSignUp): EventSignUp {
        return super.update(entity)
    }

    @Transactional(readOnly = true)
    fun findByUserIdAndEventId(userId: Long, eventId: Long): EventSignUp {
        return repository.findByUser_IdAndEvent_Id(userId, eventId)
            .orElseThrow(Supplier {
                ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "EventSignUp not found for user: $userId and event: $eventId"
                )
            })
    }

    @Transactional(readOnly = true)
    fun findByGuestAccessToken(accessToken: String): MutableList<EventSignUp> {
        return repository.findByGuestAccessTokenHash(GuestAccessTokenCodec.hash(accessToken))
    }

    fun findByEventId(eventId: Long): MutableList<EventSignUp> {
        return repository.findByEvent_Id(eventId)
    }

    fun findByFilter(filter: EventSignUpQuery): MutableList<EventSignUp> {
        val spec = EventSignUpSpecifications.fromFilter(filter, currentUserProvider.currentUser())
        return repository.findAll(spec)
    }

    fun findBySurveyId(surveyId: Long): MutableSet<EventSignUp> {
        return repository.findAllByEventSignUpForm_Id(surveyId)
    }

    fun findByGuestAccessTokenAndEventId(accessToken: String, eventId: Long): EventSignUp {
        return repository.findByGuestAccessTokenHashAndEvent_Id(GuestAccessTokenCodec.hash(accessToken), eventId)
            .orElseThrow(Supplier {
                ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "EventSignUp not found for provided guest token and event: $eventId"
                )
            })
    }
}

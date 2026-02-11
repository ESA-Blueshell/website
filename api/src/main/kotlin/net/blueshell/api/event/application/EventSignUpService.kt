package net.blueshell.api.event.application

import net.blueshell.api.event.application.event.EventSignUpCreated
import net.blueshell.api.event.persistence.Event
import net.blueshell.api.event.persistence.EventSignUp
import net.blueshell.api.event.persistence.EventSignUpRepository
import net.blueshell.api.event.persistence.filter.EventSignUpFilter
import net.blueshell.api.event.persistence.spec.EventSignUpSpecifications
import net.blueshell.api.shared.event.AfterCommitEventPublisher
import net.blueshell.api.shared.model.asRef
import net.blueshell.api.shared.service.BaseModelService
import net.blueshell.api.user.persistence.User
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.util.function.Supplier

@Service
class EventSignUpService @Autowired constructor(
    repository: EventSignUpRepository,
    private val events: AfterCommitEventPublisher
) : BaseModelService<EventSignUp, Long, EventSignUpRepository>(repository) {
    @Transactional
    override fun create(entity: EventSignUp): EventSignUp {
        mergeAssociations(entity)
        val saved = super.create(entity)
        events.publish(EventSignUpCreated(saved.id!!))
        return saved
    }

    @Transactional
    override fun update(entity: EventSignUp): EventSignUp {
        mergeAssociations(entity)
        return super.update(entity)
    }

    @Transactional(readOnly = true)
    fun findByUserIdAndEventId(userId: Long, eventId: Long): EventSignUp {
        return repository.findByUserIdAndEventId(userId, eventId)
            .orElseThrow(Supplier {
                ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "EventSignUp not found for user: $userId and event: $eventId"
                )
            })
    }

    @Transactional(readOnly = true)
    fun findByGuestAccessToken(accessToken: String): MutableList<EventSignUp> {
        return repository.findByGuestAccessToken(accessToken)
    }

    fun findByEventId(eventId: Long): MutableList<EventSignUp> {
        return repository.findByEventId(eventId)
    }

    fun findByFilter(filter: EventSignUpFilter): MutableList<EventSignUp> {
        val spec = EventSignUpSpecifications.fromFilter(filter, principal)
        return repository.findAll(spec)
    }

    fun findBySurveyId(surveyId: Long): MutableSet<EventSignUp> {
        return repository.findAllBy_eventSignUpFormId(surveyId)
    }

    fun findByGuestAccessTokenAndEventId(accessToken: String, eventId: Long): EventSignUp {
        return repository.findBy_guestAccessTokenAndEventId(accessToken, eventId)
            .orElseThrow(Supplier {
                ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "EventSignUp not found for accessToken: $accessToken and event: $eventId"
                )
            })
    }

    private fun mergeAssociations(signUp: EventSignUp) {
        // No additional merging needed for many-to-one relationships
        // They are already set as entity references in the mapper
    }
}

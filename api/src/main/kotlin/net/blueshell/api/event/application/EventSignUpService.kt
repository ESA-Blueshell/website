package net.blueshell.api.event.application

import net.blueshell.api.event.domain.model.EventSignUp
import net.blueshell.api.event.domain.model.filter.EventSignUpFilter
import net.blueshell.api.event.persistence.EventSignUpRepository
import net.blueshell.api.event.persistence.spec.EventSignUpSpecifications
import net.blueshell.api.shared.service.BaseModelService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.util.function.Supplier

@Service
class EventSignUpService @Autowired constructor(repository: EventSignUpRepository) :
    BaseModelService<EventSignUp, Long, EventSignUpRepository>(repository) {
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
}

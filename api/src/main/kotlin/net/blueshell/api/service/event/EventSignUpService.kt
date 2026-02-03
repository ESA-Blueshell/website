package net.blueshell.api.service.event

import net.blueshell.api.base.BaseModelService
import net.blueshell.api.controller.filter.EventSignUpFilter
import net.blueshell.api.model.event.EventSignUp
import net.blueshell.api.repository.event.EventSignUpRepository
import net.blueshell.api.repository.spec.EventSignUpSpecifications
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.util.function.Supplier

@Service
class EventSignUpService @Autowired constructor(repository: EventSignUpRepository) :
    BaseModelService<EventSignUp?, EventSignUpRepository?>(repository) {
    @Transactional(readOnly = true)
    fun findByUserIdAndEventId(userId: Long?, eventId: Long?): EventSignUp? {
        return repository!!.findByUserIdAndEventId(userId, eventId)
            .orElseThrow<ResponseStatusException?>(Supplier {
                ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "EventSignUp not found for user: %d and event: %d".formatted(userId, eventId)
                )
            })
    }

    @Transactional(readOnly = true)
    fun findByGuestAccessToken(accessToken: String?): MutableList<EventSignUp?>? {
        return repository!!.findByGuestAccessToken(accessToken)
    }

    fun findByEventId(eventId: Long?): MutableList<EventSignUp?>? {
        return repository!!.findByEventId(eventId)
    }

    fun findByFilter(filter: EventSignUpFilter?): MutableList<EventSignUp?> {
        var filter = filter
        if (filter == null) filter = EventSignUpFilter()
        val spec = EventSignUpSpecifications.fromFilter(filter, getPrincipal())
        return repository!!.findAll(spec)
    }

    fun findBySurveyId(surveyId: Long?): MutableSet<EventSignUp?>? {
        return repository!!.findAllByEventSignUpFormId(surveyId)
    }

    fun findByGuestAccessTokenAndEventId(accessToken: String?, eventId: Long?): EventSignUp? {
        return repository!!.findByGuestAccessTokenAndEventId(accessToken, eventId)
            .orElseThrow<ResponseStatusException?>(Supplier {
                ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "EventSignUp not found for accessToken: %s and event: %d".formatted(accessToken, eventId)
                )
            })
    }
}

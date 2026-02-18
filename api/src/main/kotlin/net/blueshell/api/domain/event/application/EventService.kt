package net.blueshell.api.domain.event.application

import net.blueshell.api.domain.event.application.event.EventChange
import net.blueshell.api.domain.event.application.event.EventChanged
import net.blueshell.api.domain.event.persistence.Event
import net.blueshell.api.domain.event.application.query.EventQuery
import net.blueshell.api.domain.event.persistence.repository.EventRepository
import net.blueshell.api.domain.event.persistence.spec.EventSpecifications
import net.blueshell.api.shared.security.CurrentUserProvider
import net.blueshell.api.shared.event.TrackedEventPublisher
import net.blueshell.api.shared.service.BaseModelService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class EventService @Autowired constructor(
    repository: EventRepository,
    private val trackedEvents: TrackedEventPublisher,
    private val currentUserProvider: CurrentUserProvider
) : BaseModelService<Event, Long, EventRepository>(repository) {
    @Transactional
    override fun create(entity: Event): Event {
        mergeAssociations(entity)
        val saved = super.create(entity)
        trackedEvents.publish { actor ->
            EventChanged(
                saved.id!!,
                EventChange.CREATED,
                actor = actor
            )
        }
        return saved
    }

    @Transactional
    override fun update(entity: Event): Event {
        mergeAssociations(entity)
        val saved = super.update(entity)
        trackedEvents.publish { actor ->
            EventChanged(
                saved.id!!,
                EventChange.UPDATED,
                actor = actor
            )
        }
        return saved
    }

    @Transactional
    override fun delete(entity: Event) {
        val eventId = entity.id!!
        super.delete(entity)
        trackedEvents.publish { actor ->
            EventChanged(
                eventId,
                EventChange.DELETED,
                actor = actor
            )
        }
    }

    @Transactional
    override fun deleteById(id: Long) {
        super.deleteById(id)
        trackedEvents.publish { actor ->
            EventChanged(
                id,
                EventChange.DELETED,
                actor = actor
            )
        }
    }

    fun findByFilter(pageable: Pageable, filter: EventQuery): Page<Event> {
        val spec = EventSpecifications.fromFilter(filter, currentUserProvider.currentUser())
        return repository.findAll(spec, pageable)
    }

    private fun mergeAssociations(event: Event) {
        // Set parent reference for one-to-one owned relationship
        event.banner?.let { banner ->
            banner.event = event
            // Replace transient file entity with reference if it has an ID
            banner.file.id?.let { fileId ->
                banner.id.fileId = fileId
            }
        }
        // Set parent references for one-to-many nested relationship
        event.signUpForm?.let { survey ->
            survey.questions.forEach { question ->
                question.survey = survey
            }
        }
    }
}

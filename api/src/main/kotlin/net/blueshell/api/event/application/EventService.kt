package net.blueshell.api.event.application

import net.blueshell.api.event.application.event.EventChange
import net.blueshell.api.event.application.event.EventChanged
import net.blueshell.api.event.persistence.Event
import net.blueshell.api.event.persistence.filter.EventFilter
import net.blueshell.api.event.persistence.EventRepository
import net.blueshell.api.event.persistence.spec.EventSpecifications
import net.blueshell.api.file.persistence.File
import net.blueshell.api.shared.event.AfterCommitEventPublisher
import net.blueshell.api.shared.model.asRef
import net.blueshell.api.shared.service.BaseModelService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class EventService @Autowired constructor(
    repository: EventRepository,
    private val events: AfterCommitEventPublisher
) : BaseModelService<Event, Long, EventRepository>(repository) {
    @Transactional
    override fun create(entity: Event): Event {
        mergeAssociations(entity)
        val saved = super.create(entity)
        events.publish(EventChanged(saved.id!!, EventChange.CREATED))
        return saved
    }

    @Transactional
    override fun update(entity: Event): Event {
        mergeAssociations(entity)
        val saved = super.update(entity)
        events.publish(EventChanged(saved.id!!, EventChange.UPDATED))
        return saved
    }

    @Transactional
    override fun delete(entity: Event) {
        val eventId = entity.id!!
        super.delete(entity)
        events.publish(EventChanged(eventId, EventChange.DELETED))
    }

    @Transactional
    override fun deleteById(id: Long) {
        super.deleteById(id)
        events.publish(EventChanged(id, EventChange.DELETED))
    }

    fun findByFilter(pageable: Pageable, filter: EventFilter): Page<Event> {
        val spec = EventSpecifications.fromFilter(filter, principal)
        return repository.findAll(spec, pageable)
    }

    private fun mergeAssociations(event: Event) {
        event.banner?.let { banner ->
            banner.event = event
            banner.file.id?.let { fileId ->
                banner.file = File::class.asRef(fileId)
            }
        }
        event.signUpForm?.let { survey ->
            survey.questions.forEach { question -> question.survey = survey }
        }
    }
}

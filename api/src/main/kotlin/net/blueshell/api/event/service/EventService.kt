package net.blueshell.api.event.service

import net.blueshell.api.event.model.Event
import net.blueshell.api.event.model.filter.EventFilter
import net.blueshell.api.event.repository.EventRepository
import net.blueshell.api.event.repository.spec.EventSpecifications
import net.blueshell.api.shared.service.BaseModelService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service

@Service
class EventService @Autowired constructor(repository: EventRepository) :
    BaseModelService<Event, Long, EventRepository>(repository) {
    fun findByFilter(pageable: Pageable, filter: EventFilter): Page<Event> {
        val spec = EventSpecifications.fromFilter(filter, principal)
        return repository.findAll(spec, pageable)
    }
}

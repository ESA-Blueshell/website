package net.blueshell.api.service.event

import net.blueshell.api.model.event.Event
import net.blueshell.api.model.filter.EventFilter
import net.blueshell.api.repository.event.EventRepository
import net.blueshell.api.repository.spec.EventSpecifications
import net.blueshell.api.service.base.BaseModelService
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

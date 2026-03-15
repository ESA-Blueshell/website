package net.blueshell.api.domain.event.application

import net.blueshell.api.domain.event.persistence.Event
import net.blueshell.api.domain.event.persistence.EventBanner
import net.blueshell.api.domain.event.persistence.repository.EventBannerRepository
import net.blueshell.api.domain.event.persistence.repository.EventRepository
import net.blueshell.api.shared.service.BaseModelService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class EventBannerService @Autowired constructor(
    repository: EventBannerRepository,
    private val eventRepository: EventRepository
) : BaseModelService<EventBanner, EventBanner.Id, EventBannerRepository>(repository) {
    @Transactional
    override fun create(entity: EventBanner): EventBanner {
        mergeRefs(entity)
        return super.create(entity)
    }

    @Transactional
    override fun update(entity: EventBanner): EventBanner {
        mergeRefs(entity)
        return super.update(entity)
    }

    private fun mergeRefs(banner: EventBanner) {
        banner.id.eventId?.let { banner.event = eventRepository.getReferenceById(it) }
        banner.id.fileId?.let { banner.id.fileId = it }
    }
}

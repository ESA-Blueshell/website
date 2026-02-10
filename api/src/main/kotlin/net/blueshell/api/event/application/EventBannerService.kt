package net.blueshell.api.event.application

import net.blueshell.api.event.persistence.Event
import net.blueshell.api.event.persistence.EventBanner
import net.blueshell.api.event.persistence.EventBannerRepository
import net.blueshell.api.file.persistence.File
import net.blueshell.api.shared.model.asRef
import net.blueshell.api.shared.service.BaseModelService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class EventBannerService @Autowired constructor(
    repository: EventBannerRepository
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
        banner.id.eventId?.let { banner.event = Event::class.asRef(it) }
        banner.id.fileId?.let { banner.file = File::class.asRef(it) }
    }
}

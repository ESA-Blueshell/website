package net.blueshell.api.domain.event.application

import net.blueshell.api.domain.event.persistence.repository.EventBannerRepository
import net.blueshell.api.file.api.EventBannerFileLookup
import org.springframework.stereotype.Component

@Component
class EventBannerFileResolver(
    private val banners: EventBannerRepository,
) : EventBannerFileLookup {
    override fun fileIdForEvent(eventId: Long): Long? =
        banners.findFirstByIdEventId(eventId).orElse(null)?.id?.fileId
}

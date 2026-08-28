package net.blueshell.api.event.domain

import net.blueshell.api.event.persistence.EventBannerRepository
import net.blueshell.api.file.api.EventBannerFileLookup
import org.springframework.stereotype.Component

@Component
class EventBannerFileResolver(
    private val banners: EventBannerRepository,
) : EventBannerFileLookup {
    override fun fileIdForEvent(eventId: Long): Long? =
        banners.findFirstByIdEventId(eventId).orElse(null)?.id?.fileId
}

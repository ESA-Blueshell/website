package net.blueshell.api.event.domain

import net.blueshell.api.event.persistence.EventBanner
import net.blueshell.api.event.persistence.EventBannerRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class EventBannerService(
    private val repository: EventBannerRepository,
) {
    @Transactional(readOnly = true)
    fun findById(id: EventBanner.Id): EventBanner =
        repository.findById(id).orElseThrow { EventBannerNotFoundException(id.eventId ?: 0, id.fileId ?: 0) }
}

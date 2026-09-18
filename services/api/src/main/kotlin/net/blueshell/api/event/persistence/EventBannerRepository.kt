package net.blueshell.api.event.persistence

import net.blueshell.api.shared.repository.BaseRepository
import org.springframework.stereotype.Repository
import java.util.*

@Repository
interface EventBannerRepository : BaseRepository<EventBanner, EventBanner.Id> {
    fun countByIdFileId(fileId: Long): Long

    fun findFirstByIdEventId(eventId: Long): Optional<EventBanner>
}

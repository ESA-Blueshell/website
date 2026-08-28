package net.blueshell.api.event.persistence

import net.blueshell.api.shared.repository.BaseRepository
import java.util.*
import org.springframework.stereotype.Repository

@Repository
interface EventBannerRepository : BaseRepository<EventBanner, EventBanner.Id> {
    fun countByIdFileId(fileId: Long): Long

    fun findFirstByIdEventId(eventId: Long): Optional<EventBanner>
}

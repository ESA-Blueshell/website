package net.blueshell.api.event.persistence

import net.blueshell.api.event.domain.model.EventBanner
import net.blueshell.api.shared.repository.BaseRepository
import org.springframework.stereotype.Repository

@Repository
interface EventBannerRepository : BaseRepository<EventBanner, EventBanner.Id>

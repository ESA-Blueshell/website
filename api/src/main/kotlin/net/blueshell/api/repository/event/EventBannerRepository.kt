package net.blueshell.api.repository.event

import net.blueshell.api.model.event.EventBanner
import net.blueshell.api.repository.base.BaseRepository
import org.springframework.stereotype.Repository

@Repository
interface EventBannerRepository : BaseRepository<EventBanner, EventBanner.Id>

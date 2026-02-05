package net.blueshell.api.repository.event

import net.blueshell.api.repository.base.BaseRepository
import net.blueshell.api.model.event.EventBanner
import net.blueshell.api.model.event.EventBannerId
import org.springframework.stereotype.Repository

@Repository
interface EventBannerRepository : BaseRepository<EventBanner, EventBannerId>

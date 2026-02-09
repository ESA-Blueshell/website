package net.blueshell.api.event.repository

import net.blueshell.api.event.model.EventBanner
import net.blueshell.api.shared.repository.BaseRepository
import org.springframework.stereotype.Repository

@Repository
interface EventBannerRepository : BaseRepository<EventBanner, EventBanner.Id>

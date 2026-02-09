package net.blueshell.api.feature.event.repository

import net.blueshell.api.feature.event.model.EventBanner
import net.blueshell.api.shared.repository.BaseRepository
import org.springframework.stereotype.Repository

@Repository
interface EventBannerRepository : BaseRepository<EventBanner, EventBanner.Id>

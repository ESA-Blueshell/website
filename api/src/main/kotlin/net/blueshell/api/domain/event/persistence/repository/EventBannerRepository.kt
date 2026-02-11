package net.blueshell.api.domain.event.persistence.repository

import net.blueshell.api.domain.event.persistence.EventBanner
import net.blueshell.api.shared.repository.BaseRepository
import org.springframework.stereotype.Repository

@Repository
interface EventBannerRepository : BaseRepository<EventBanner, EventBanner.Id>

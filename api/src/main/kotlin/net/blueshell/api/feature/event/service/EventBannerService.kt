package net.blueshell.api.feature.event.service

import net.blueshell.api.feature.event.model.EventBanner
import net.blueshell.api.feature.event.repository.EventBannerRepository
import net.blueshell.api.shared.service.BaseModelService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service

@Service
class EventBannerService @Autowired constructor(
    repository: EventBannerRepository
) : BaseModelService<EventBanner, EventBanner.Id, EventBannerRepository>(repository)

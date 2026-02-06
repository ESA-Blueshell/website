package net.blueshell.api.service.event

import net.blueshell.api.model.event.EventBanner
import net.blueshell.api.repository.event.EventBannerRepository
import net.blueshell.api.service.base.BaseModelService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service

@Service
class EventBannerService @Autowired constructor(
    repository: EventBannerRepository
) : BaseModelService<EventBanner, EventBanner.Id, EventBannerRepository>(repository)

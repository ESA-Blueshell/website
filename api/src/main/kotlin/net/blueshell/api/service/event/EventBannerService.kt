package net.blueshell.api.service.event

import net.blueshell.api.base.BaseModelService
import net.blueshell.api.model.event.EventBanner
import net.blueshell.api.repository.event.EventBannerRepository
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service

@Service
class EventBannerService @Autowired constructor(
    repository: EventBannerRepository
) : BaseModelService<EventBanner, Long, EventBannerRepository>(repository)

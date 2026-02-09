package net.blueshell.api.event.application

import net.blueshell.api.event.persistence.EventBanner
import net.blueshell.api.event.persistence.EventBannerRepository
import net.blueshell.api.shared.service.BaseModelService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service

@Service
class EventBannerService @Autowired constructor(
    repository: EventBannerRepository
) : BaseModelService<EventBanner, EventBanner.Id, EventBannerRepository>(repository)

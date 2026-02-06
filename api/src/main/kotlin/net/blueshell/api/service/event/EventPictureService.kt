package net.blueshell.api.service.event

import net.blueshell.api.model.event.EventPicture
import net.blueshell.api.repository.event.EventPictureRepository
import net.blueshell.api.service.base.BaseModelService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service

@Service
class EventPictureService @Autowired constructor(
    repository: EventPictureRepository,
    events: ApplicationEventPublisher
) : BaseModelService<EventPicture, EventPicture.Id, EventPictureRepository>(repository)

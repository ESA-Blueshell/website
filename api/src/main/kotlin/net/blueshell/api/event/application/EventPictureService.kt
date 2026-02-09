package net.blueshell.api.event.application

import net.blueshell.api.event.persistence.EventPicture
import net.blueshell.api.event.persistence.EventPictureRepository
import net.blueshell.api.shared.service.BaseModelService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service

@Service
class EventPictureService @Autowired constructor(
    repository: EventPictureRepository,
    events: ApplicationEventPublisher
) : BaseModelService<EventPicture, EventPicture.Id, EventPictureRepository>(repository)

package net.blueshell.api.repository.event

import net.blueshell.api.base.BaseRepository
import net.blueshell.api.model.event.EventPicture
import net.blueshell.api.model.event.EventPictureId
import org.springframework.stereotype.Repository

@Repository
interface EventPictureRepository : BaseRepository<EventPicture, EventPictureId>

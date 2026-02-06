package net.blueshell.api.repository.event

import net.blueshell.api.model.event.EventPicture
import net.blueshell.api.repository.base.BaseRepository
import org.springframework.stereotype.Repository

@Repository
interface EventPictureRepository : BaseRepository<EventPicture, EventPicture.Id>

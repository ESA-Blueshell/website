package net.blueshell.api.event.repository

import net.blueshell.api.event.model.EventPicture
import net.blueshell.api.shared.repository.BaseRepository
import org.springframework.stereotype.Repository

@Repository
interface EventPictureRepository : BaseRepository<EventPicture, EventPicture.Id>

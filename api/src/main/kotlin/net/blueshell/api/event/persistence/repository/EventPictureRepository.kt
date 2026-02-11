package net.blueshell.api.event.persistence.repository

import net.blueshell.api.event.persistence.EventPicture
import net.blueshell.api.shared.repository.BaseRepository
import org.springframework.stereotype.Repository

@Repository
interface EventPictureRepository : BaseRepository<EventPicture, EventPicture.Id>

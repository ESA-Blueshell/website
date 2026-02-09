package net.blueshell.api.event.persistence

import net.blueshell.api.event.domain.model.EventPicture
import net.blueshell.api.shared.repository.BaseRepository
import org.springframework.stereotype.Repository

@Repository
interface EventPictureRepository : BaseRepository<EventPicture, EventPicture.Id>

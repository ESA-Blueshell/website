package net.blueshell.api.domain.event.persistence.repository

import net.blueshell.api.domain.event.persistence.EventPicture
import net.blueshell.api.shared.repository.BaseRepository
import org.springframework.stereotype.Repository

@Repository
interface EventPictureRepository : BaseRepository<EventPicture, EventPicture.Id>

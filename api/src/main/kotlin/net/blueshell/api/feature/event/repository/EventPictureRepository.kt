package net.blueshell.api.feature.event.repository

import net.blueshell.api.feature.event.model.EventPicture
import net.blueshell.api.shared.repository.BaseRepository
import org.springframework.stereotype.Repository

@Repository
interface EventPictureRepository : BaseRepository<EventPicture, EventPicture.Id>

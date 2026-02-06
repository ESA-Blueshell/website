package net.blueshell.api.repository.event

import net.blueshell.api.model.event.EventFeedback
import net.blueshell.api.repository.base.BaseRepository
import org.springframework.stereotype.Repository

@Repository
interface EventFeedbackRepository : BaseRepository<EventFeedback, Long>

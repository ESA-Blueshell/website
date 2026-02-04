package net.blueshell.api.repository.event

import net.blueshell.api.base.BaseRepository
import net.blueshell.api.model.event.EventFeedback
import org.springframework.stereotype.Repository

@Repository
interface EventFeedbackRepository : BaseRepository<EventFeedback, Long>

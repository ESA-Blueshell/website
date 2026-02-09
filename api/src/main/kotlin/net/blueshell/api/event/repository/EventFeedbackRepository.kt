package net.blueshell.api.event.repository

import net.blueshell.api.event.model.EventFeedback
import net.blueshell.api.shared.repository.BaseRepository
import org.springframework.stereotype.Repository

@Repository
interface EventFeedbackRepository : BaseRepository<EventFeedback, Long>

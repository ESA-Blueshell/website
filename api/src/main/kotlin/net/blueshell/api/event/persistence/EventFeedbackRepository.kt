package net.blueshell.api.event.persistence

import net.blueshell.api.event.domain.model.EventFeedback
import net.blueshell.api.shared.repository.BaseRepository
import org.springframework.stereotype.Repository

@Repository
interface EventFeedbackRepository : BaseRepository<EventFeedback, Long>

package net.blueshell.api.domain.event.persistence.repository

import net.blueshell.api.domain.event.persistence.EventFeedback
import net.blueshell.api.shared.repository.BaseRepository
import org.springframework.stereotype.Repository

@Repository
interface EventFeedbackRepository : BaseRepository<EventFeedback, Long>

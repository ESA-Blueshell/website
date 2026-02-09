package net.blueshell.api.feature.event.repository

import net.blueshell.api.feature.event.model.EventFeedback
import net.blueshell.api.shared.repository.BaseRepository
import org.springframework.stereotype.Repository

@Repository
interface EventFeedbackRepository : BaseRepository<EventFeedback, Long>

package net.blueshell.api.event.web

import io.swagger.v3.oas.annotations.media.Schema
import net.blueshell.api.event.domain.EventField

@Schema(name = "QueuedEvent", description = "An event waiting for the board")
data class QueuedEventResponse(
    val event: EventResponse,
    @param:Schema(description = "Whether it was approved before and changed since")
    val reapproval: Boolean,
    @param:Schema(description = "What changed since it was last approved; empty for a new event, or one approved before this was kept")
    val changes: List<EventField>,
)

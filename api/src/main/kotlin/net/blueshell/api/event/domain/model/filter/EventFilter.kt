package net.blueshell.api.event.domain.model.filter

import org.springframework.format.annotation.DateTimeFormat
import java.time.LocalDateTime

class EventFilter {
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    var from: LocalDateTime? = null

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    var to: LocalDateTime? = null

    var approved: Boolean? = null

    var committeeId: Long? = null
    var titleContains: String? = null
}

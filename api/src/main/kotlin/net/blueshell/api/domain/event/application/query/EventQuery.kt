package net.blueshell.api.domain.event.application.query

import org.springframework.format.annotation.DateTimeFormat
import java.time.LocalDateTime

data class EventQuery(
    @field:DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    var from: LocalDateTime? = null,

    @field:DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    var to: LocalDateTime? = null,

    var approved: Boolean? = null,

    var committeeId: Long? = null,
    var titleContains: String? = null
)

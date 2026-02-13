package net.blueshell.api.domain.event.application.query

import org.springframework.format.annotation.DateTimeFormat
import java.time.LocalDateTime

data class EventSignUpQuery(
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    var from: LocalDateTime? = null,

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    var to: LocalDateTime? = null,
    var userId: Long? = null,
    var committeeId: Long? = null,
    var approved: Boolean? = null,
    var eventId: Long? = null
)

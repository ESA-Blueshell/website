package net.blueshell.api.controller.filter

import lombok.Data
import org.springframework.format.annotation.DateTimeFormat
import java.time.LocalDateTime

@Data
class EventFilter {
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private val from: LocalDateTime? = null

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private val to: LocalDateTime? = null

    private val approved: Boolean? = null

    private val committeeId: Long? = null
    private val titleContains: String? = null
}
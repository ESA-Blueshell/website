package net.blueshell.api.event.web

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.Valid
import jakarta.validation.constraints.NotNull
import net.blueshell.api.survey.web.AnswerResponse
import net.blueshell.api.user.web.UserSummaryResponse
import java.time.Instant

@Schema(name = "EventSignUpResponse")
data class EventSignUpResponse(
    var id: Long,

    @field:NotNull
    var eventId: Long,

    @field:Valid
    var answers: MutableList<AnswerResponse> = mutableListOf(),

    var guest: GuestResponse? = null,
    var user: UserSummaryResponse? = null,
    var version: Long,
    var createdAt: Instant,
    var updatedAt: Instant
)

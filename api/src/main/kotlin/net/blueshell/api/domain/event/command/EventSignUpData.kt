package net.blueshell.api.domain.event.command

import jakarta.validation.Valid
import jakarta.validation.constraints.NotNull
import net.blueshell.api.domain.survey.application.validation.ValidAnswerList
import net.blueshell.api.domain.survey.command.AnswerData

/**
 * Event sign-up information for commands.
 * Command-layer data structure (not a web DTO).
 */
data class EventSignUpData(
    @field:NotNull(message = "Event ID is required")
    val eventId: Long,

    @field:ValidAnswerList
    @field:Valid
    val answers: List<AnswerData> = emptyList(),

    @field:Valid
    val guest: GuestData? = null,

    val userId: Long? = null,
    val version: Long? = null
)

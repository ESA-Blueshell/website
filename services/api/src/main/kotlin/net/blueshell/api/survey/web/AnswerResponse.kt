package net.blueshell.api.survey.web

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotNull
import java.time.Instant

@Schema(name = "AnswerResponse")
data class AnswerResponse(
    var id: Long,

    @field:NotNull
    var questionId: Long,

    var optionSelections: MutableList<Boolean>? = null,

    var textResponse: String? = null,
    var version: Long,
    var createdAt: Instant,
    var updatedAt: Instant,
)

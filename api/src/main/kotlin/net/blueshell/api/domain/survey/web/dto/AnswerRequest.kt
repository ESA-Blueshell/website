package net.blueshell.api.domain.survey.web.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotNull

@Schema(name = "AnswerRequest")
data class AnswerRequest(
    @field:NotNull
    var questionId: Long? = null,

    var optionSelections: MutableList<Boolean>? = null,

    var textResponse: String? = null
)

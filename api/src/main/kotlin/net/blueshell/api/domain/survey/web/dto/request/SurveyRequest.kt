package net.blueshell.api.domain.survey.web.dto.request

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.Valid
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.NotNull

@Schema(name = "SurveyRequest")
data class SurveyRequest(
    @field:NotEmpty
    @field:NotNull
    @field:Valid
    var questions: MutableList<QuestionRequest>? = null
)

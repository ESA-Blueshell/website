package net.blueshell.api.survey.web

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.Valid
import jakarta.validation.constraints.NotEmpty

@Schema(name = "SurveyRequest")
data class SurveyRequest(
    @field:NotEmpty
    @field:Valid
    var questions: MutableList<QuestionRequest>
)

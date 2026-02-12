package net.blueshell.api.domain.survey.web.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.Valid
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.NotNull
import net.blueshell.api.domain.survey.web.validation.ValidQuestionList
import net.blueshell.api.shared.dto.BaseDTO

@Schema(name = "SurveyRequest")
data class SurveyRequest(
    @field:NotEmpty
    @field:NotNull
    @field:ValidQuestionList
    @field:Valid
    var questions: MutableList<QuestionRequest>? = null
) : BaseDTO()

package net.blueshell.api.domain.survey.command

import jakarta.validation.Valid
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.NotNull
import net.blueshell.api.shared.enums.QuestionType

data class SurveyData(
    @field:NotEmpty(message = "Survey must have at least one question")
    @field:Valid
    var questions: List<QuestionData>
)

data class QuestionData(
    @field:NotNull(message = "Question index is required")
    var idx: Long,

    @field:NotNull(message = "Question type is required")
    var type: QuestionType,

    @field:NotNull(message = "Question label is required")
    var label: String,

    val choiceLabels: List<String>?
)

package net.blueshell.api.domain.survey.command

import jakarta.validation.Valid
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.NotNull
import net.blueshell.api.domain.survey.application.validation.ValidQuestion
import net.blueshell.api.domain.survey.application.validation.ValidQuestionList
import net.blueshell.api.shared.enums.QuestionType

data class SurveyData(
    @field:NotEmpty(message = "Survey must have at least one question")
    @field:ValidQuestionList
    @field:Valid
    var questions: List<QuestionData>
)

@ValidQuestion
data class QuestionData(
    @field:NotNull(message = "Question index is required")
    override var idx: Long,

    @field:NotNull(message = "Question type is required")
    override var type: QuestionType,

    @field:NotNull(message = "Question label is required")
    override var label: String,

    override val choiceLabels: List<String>?,

    override val required: Boolean = false,
) : QuestionCandidate

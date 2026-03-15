package net.blueshell.api.domain.survey.command

import jakarta.validation.Valid
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.NotNull
import net.blueshell.api.domain.survey.application.validation.ValidQuestionList
import net.blueshell.api.domain.survey.persistence.Survey
import net.blueshell.api.shared.command.Command

data class CreateSurveyCommand(
    @field:NotEmpty(message = "Survey must have at least one question")
    @field:ValidQuestionList
    @field:Valid
    var questions: MutableList<QuestionData>
) : Command<Survey>

data class UpdateSurveyCommand(
    @field:NotNull(message = "Survey ID is required")
    var id: Long,

    @field:NotEmpty(message = "Survey must have at least one question")
    @field:ValidQuestionList
    @field:Valid
    var questions: MutableList<QuestionData>
) : Command<Survey>

class FindSurveysCommand : Command<MutableList<Survey>>

data class FindSurveyByIdCommand(
    @field:NotNull(message = "Survey ID is required")
    var id: Long
) : Command<Survey>

data class DeleteSurveyByIdCommand(
    @field:NotNull(message = "Survey ID is required")
    var id: Long
) : Command<Unit>

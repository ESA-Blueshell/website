package net.blueshell.api.domain.survey.command

import jakarta.validation.constraints.NotNull
import net.blueshell.api.domain.survey.persistence.Answer
import net.blueshell.api.shared.command.Command

data class CreateAnswerCommand(
    @field:NotNull(message = "Question ID is required")
    var questionId: Long,

    val optionSelections: MutableList<Boolean>?,

    val textResponse: String?
) : Command<Answer>

data class UpdateAnswerCommand(
    @field:NotNull(message = "Answer ID is required")
    var id: Long,

    @field:NotNull(message = "Question ID is required")
    var questionId: Long,

    val optionSelections: MutableList<Boolean>?,

    val textResponse: String?
) : Command<Answer>

class FindAnswersCommand : Command<MutableList<Answer>>

data class FindAnswerByIdCommand(
    @field:NotNull(message = "Answer ID is required")
    var id: Long
) : Command<Answer>

data class FindAnswersBySurveyIdCommand(
    @field:NotNull(message = "Survey ID is required")
    var surveyId: Long
) : Command<MutableSet<Answer>>

data class FindAnswersByQuestionIdCommand(
    @field:NotNull(message = "Question ID is required")
    var questionId: Long
) : Command<MutableSet<Answer>>

data class DeleteAnswerByIdCommand(
    @field:NotNull(message = "Answer ID is required")
    var id: Long
) : Command<Unit>

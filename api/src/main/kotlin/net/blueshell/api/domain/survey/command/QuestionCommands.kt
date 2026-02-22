package net.blueshell.api.domain.survey.command

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import net.blueshell.api.domain.survey.application.validation.ValidQuestion
import net.blueshell.api.domain.survey.persistence.Question
import net.blueshell.api.shared.command.Command
import net.blueshell.api.shared.enums.QuestionType

@ValidQuestion
data class CreateQuestionCommand(
    @field:NotNull(message = "Question index is required")
    override var idx: Long,

    @field:NotNull(message = "Survey ID is required")
    var surveyId: Long,

    @field:NotNull(message = "Question type is required")
    override var type: QuestionType,

    @field:NotBlank(message = "Question label is required")
    @field:Size(max = 2055, message = "Label cannot exceed 2055 characters")
    override var label: String,

    override val choiceLabels: MutableList<String>?
) : Command<Question>, QuestionCandidate

@ValidQuestion
data class UpdateQuestionCommand(
    @field:NotNull(message = "Question ID is required")
    var id: Long,

    @field:NotNull(message = "Question index is required")
    override var idx: Long,

    @field:NotNull(message = "Survey ID is required")
    var surveyId: Long,

    @field:NotNull(message = "Question type is required")
    override var type: QuestionType,

    @field:NotBlank(message = "Question label is required")
    @field:Size(max = 2055, message = "Label cannot exceed 2055 characters")
    override var label: String,

    override val choiceLabels: MutableList<String>?
) : Command<Question>, QuestionCandidate

class FindQuestionsCommand : Command<MutableList<Question>>

data class FindQuestionByIdCommand(
    @field:NotNull(message = "Question ID is required")
    var id: Long
) : Command<Question>

data class DeleteQuestionByIdCommand(
    @field:NotNull(message = "Question ID is required")
    var id: Long
) : Command<Unit>

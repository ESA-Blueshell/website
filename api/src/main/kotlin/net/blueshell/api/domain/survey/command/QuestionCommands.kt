package net.blueshell.api.domain.survey.command

import jakarta.validation.Valid
import jakarta.validation.constraints.NotNull
import net.blueshell.api.domain.survey.persistence.Question
import net.blueshell.api.domain.survey.web.dto.QuestionDTO
import net.blueshell.api.shared.command.Command

data class CreateQuestionCommand(
    @field:Valid
    @field:NotNull(message = "Question DTO is required")
    var dto: QuestionDTO
) : Command<Question>

data class UpdateQuestionCommand(
    @field:NotNull(message = "Question ID is required")
    var id: Long,

    @field:Valid
    @field:NotNull(message = "Question DTO is required")
    var dto: QuestionDTO
) : Command<Question>

class FindQuestionsCommand : Command<MutableList<Question>>

data class FindQuestionByIdCommand(
    @field:NotNull(message = "Question ID is required")
    var id: Long
) : Command<Question>

data class DeleteQuestionByIdCommand(
    @field:NotNull(message = "Question ID is required")
    var id: Long
) : Command<Unit>

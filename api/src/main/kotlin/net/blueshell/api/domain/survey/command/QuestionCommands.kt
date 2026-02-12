package net.blueshell.api.domain.survey.command

import jakarta.validation.Valid
import net.blueshell.api.domain.survey.persistence.Question
import net.blueshell.api.domain.survey.web.dto.QuestionDTO
import net.blueshell.api.shared.command.Command

data class CreateQuestionCommand(
    @field:Valid
    val dto: QuestionDTO
) : Command<Question>

data class UpdateQuestionCommand(
    val id: Long,
    @field:Valid
    val dto: QuestionDTO
) : Command<Question>

class FindQuestionsCommand : Command<MutableList<Question>>

data class FindQuestionByIdCommand(
    val id: Long
) : Command<Question>

data class DeleteQuestionByIdCommand(
    val id: Long
) : Command<Unit>

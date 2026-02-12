package net.blueshell.api.domain.survey.command

import jakarta.validation.Valid
import net.blueshell.api.domain.survey.persistence.Answer
import net.blueshell.api.domain.survey.web.dto.AnswerDTO
import net.blueshell.api.shared.command.Command

data class CreateAnswerCommand(
    @field:Valid
    val dto: AnswerDTO
) : Command<Answer>

data class UpdateAnswerCommand(
    val id: Long,
    @field:Valid
    val dto: AnswerDTO
) : Command<Answer>

class FindAnswersCommand : Command<MutableList<Answer>>

data class FindAnswerByIdCommand(
    val id: Long
) : Command<Answer>

data class FindAnswersBySurveyIdCommand(
    val surveyId: Long
) : Command<MutableSet<Answer>>

data class FindAnswersByQuestionIdCommand(
    val questionId: Long
) : Command<MutableSet<Answer>>

data class DeleteAnswerByIdCommand(
    val id: Long
) : Command<Unit>

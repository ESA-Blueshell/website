package net.blueshell.api.domain.survey.command

import jakarta.validation.Valid
import net.blueshell.api.domain.survey.persistence.Survey
import net.blueshell.api.domain.survey.web.dto.SurveyDTO
import net.blueshell.api.shared.command.Command

data class CreateSurveyCommand(
    @field:Valid
    val dto: SurveyDTO
) : Command<Survey>

data class UpdateSurveyCommand(
    val id: Long,
    @field:Valid
    val dto: SurveyDTO
) : Command<Survey>

class FindSurveysCommand : Command<MutableList<Survey>>

data class FindSurveyByIdCommand(
    val id: Long
) : Command<Survey>

data class DeleteSurveyByIdCommand(
    val id: Long
) : Command<Unit>

package net.blueshell.api.domain.survey.command

import jakarta.validation.Valid
import jakarta.validation.constraints.NotNull
import net.blueshell.api.domain.survey.persistence.Survey
import net.blueshell.api.domain.survey.web.dto.SurveyDTO
import net.blueshell.api.shared.command.Command

data class CreateSurveyCommand(
    @field:Valid
    @field:NotNull(message = "Survey DTO is required")
    var dto: SurveyDTO
) : Command<Survey>

data class UpdateSurveyCommand(
    @field:NotNull(message = "Survey ID is required")
    var id: Long,

    @field:Valid
    @field:NotNull(message = "Survey DTO is required")
    var dto: SurveyDTO
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

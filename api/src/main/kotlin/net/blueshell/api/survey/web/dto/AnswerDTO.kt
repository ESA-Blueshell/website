package net.blueshell.api.survey.web.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotNull
import net.blueshell.api.shared.dto.AuditedAutoIdDTO
import net.blueshell.api.survey.web.validation.ValidAnswer

@Schema(name = "Answer")
@ValidAnswer
data class AnswerDTO(
    @field:NotNull
    var questionId: Long,

    var optionSelections: MutableList<Boolean>? = null,

    var textResponse: String? = null
) : AuditedAutoIdDTO()

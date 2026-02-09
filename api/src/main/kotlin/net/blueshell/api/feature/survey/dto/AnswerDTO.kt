package net.blueshell.api.feature.survey.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotNull
import net.blueshell.api.shared.dto.AuditedAutoIdDTO
import net.blueshell.api.feature.survey.validation.ValidAnswer

@Schema(name = "Answer")
@ValidAnswer
data class AnswerDTO(
    @field:NotNull
    var questionId: Long? = null,

    var optionSelections: MutableList<Boolean>? = null,

    var textResponse: String? = null
) : AuditedAutoIdDTO()

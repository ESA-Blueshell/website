package net.blueshell.api.domain.survey.web.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotNull
import net.blueshell.api.domain.survey.web.validation.ValidAnswer
import net.blueshell.api.shared.dto.AuditedAutoIdDTO

@Schema(name = "Answer")
@ValidAnswer
data class AnswerDTO(
    @field:NotNull
    var questionId: Long? = null,

    var optionSelections: MutableList<Boolean>? = null,

    var textResponse: String? = null
) : AuditedAutoIdDTO()

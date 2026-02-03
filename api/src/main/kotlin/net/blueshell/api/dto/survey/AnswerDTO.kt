package net.blueshell.api.dto.survey

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotNull
import net.blueshell.api.base.BaseDTO
import net.blueshell.api.validation.survey.ValidAnswer

@Schema(name = "Answer")
@ValidAnswer
data class AnswerDTO(
    @field:NotNull
    var questionId: Long? = null,

    var optionSelections: MutableList<Boolean?>? = null,

    var textResponse: String? = null
) : BaseDTO()

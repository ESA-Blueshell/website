package net.blueshell.api.domain.survey.web.dto.request

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import net.blueshell.api.shared.enums.QuestionType

@Schema(name = "QuestionRequest")
data class QuestionRequest(
    var idx: Long,

    var type: QuestionType,

    @field:NotBlank(message = "Label cannot be empty.")
    @field:Size(max = 2055, message = "Label cannot exceed 2055 characters.")
    var label: String,

    var choiceLabels: MutableList<String>? = null,

    var required: Boolean? = false,
)

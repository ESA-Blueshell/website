package net.blueshell.api.survey.web

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import net.blueshell.api.shared.enums.QuestionType
import net.blueshell.api.shared.model.DESCRIPTION_MAX

@Schema(name = "QuestionRequest")
data class QuestionRequest(
    var idx: Long,
    var type: QuestionType,
    @field:NotBlank(message = "Label cannot be empty.")
    @field:Size(max = DESCRIPTION_MAX, message = "Label cannot exceed $DESCRIPTION_MAX characters.")
    var label: String,
    var choiceLabels: MutableList<String>? = null,
    var required: Boolean? = false,
)

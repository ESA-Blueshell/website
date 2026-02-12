package net.blueshell.api.domain.survey.web.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import net.blueshell.api.shared.dto.AuditedAutoIdDTO
import net.blueshell.api.shared.enums.QuestionType

@Schema(name = "QuestionResponse")
data class QuestionResponse(
    @field:NotNull
    var idx: Long? = null,

    @field:NotNull
    var surveyId: Long? = null,

    @field:NotNull
    var type: QuestionType? = null,

    @field:NotBlank(message = "Label cannot be empty.")
    @field:Size(max = 2055, message = "Label cannot exceed 2055 characters.")
    var label: String? = null,

    var choiceLabels: MutableList<String>? = null
) : AuditedAutoIdDTO()

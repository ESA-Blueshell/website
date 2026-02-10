package net.blueshell.api.survey.web.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import net.blueshell.api.shared.enums.QuestionType
import net.blueshell.api.shared.dto.AuditedAutoIdDTO

@Schema(name = "Question")
@net.blueshell.api.survey.web.validation.ValidQuestion
data class QuestionDTO(
    @field:NotNull
    var idx: Long,
    var surveyId: Long,

    @field:NotNull
    var type: QuestionType,

    @field:NotBlank(message = "Label cannot be empty.")
    @field:Size(max = 2055, message = "Label cannot exceed 2055 characters.")
    var label: String,
    var choiceLabels: MutableList<String>? = null
) : AuditedAutoIdDTO()

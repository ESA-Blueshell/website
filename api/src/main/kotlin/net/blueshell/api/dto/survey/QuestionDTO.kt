package net.blueshell.api.dto.survey

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import net.blueshell.api.base.BaseDTO
import net.blueshell.api.common.enums.QuestionType
import net.blueshell.api.validation.survey.ValidQuestion

@Schema(name = "Question")
@ValidQuestion
data class QuestionDTO(
    @field:NotNull
    var idx: Long? = null,
    var surveyId: Long? = null,

    @field:NotNull
    var type: QuestionType? = null,

    @field:NotBlank(message = "Label cannot be empty.")
    @field:Size(max = 2055, message = "Label cannot exceed 2055 characters.")
    var label: String? = null,
    var choiceLabels: MutableList<String?>? = null
) : BaseDTO()

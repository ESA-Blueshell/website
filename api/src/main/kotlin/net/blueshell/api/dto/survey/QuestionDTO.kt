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
class QuestionDTO : BaseDTO() {
    @NotNull
    val idx: @NotNull Long? = null
    val surveyId: Long? = null

    @NotNull
    val type: @NotNull QuestionType? = null

    @NotBlank(message = "Label cannot be empty.")
    @Size(max = 2055, message = "Label cannot exceed 2055 characters.")
    val label: @NotBlank(message = "Label cannot be empty.") @Size(
        max = 2055,
        message = "Label cannot exceed 2055 characters."
    ) String? = null
    val choiceLabels: MutableList<String?>? = null
}

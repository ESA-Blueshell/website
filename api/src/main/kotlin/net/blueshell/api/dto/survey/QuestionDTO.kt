package net.blueshell.api.dto.survey

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import lombok.Data
import lombok.EqualsAndHashCode
import net.blueshell.api.base.BaseDTO
import net.blueshell.api.common.enums.QuestionType
import net.blueshell.api.validation.survey.ValidQuestion

@Data
@EqualsAndHashCode(onlyExplicitlyIncluded = true, callSuper = true)
@Schema(name = "Question")
@ValidQuestion
class QuestionDTO : BaseDTO() {
    private val id: Long? = null

    @NotNull
    private val idx: @NotNull Long? = null
    private val surveyId: Long? = null

    @NotNull
    private val type: @NotNull QuestionType? = null

    @NotBlank(message = "Label cannot be empty.")
    @Size(max = 2055, message = "Label cannot exceed 2055 characters.")
    private val label: @NotBlank(message = "Label cannot be empty.") @Size(
        max = 2055,
        message = "Label cannot exceed 2055 characters."
    ) String? = null
    private val choiceLabels: MutableList<String?>? = null
}

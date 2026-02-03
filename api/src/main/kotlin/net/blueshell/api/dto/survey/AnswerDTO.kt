package net.blueshell.api.dto.survey

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotNull
import net.blueshell.api.base.BaseDTO
import net.blueshell.api.validation.survey.ValidAnswer
@Schema(name = "Answer")
@ValidAnswer
class AnswerDTO : BaseDTO() {
    @NotNull
    val questionId: @NotNull Long? = null

    val optionSelections: MutableList<Boolean?>? = null

    val textResponse: String? = null
}

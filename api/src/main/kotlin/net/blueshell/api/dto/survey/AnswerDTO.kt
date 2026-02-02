package net.blueshell.api.dto.survey

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotNull
import lombok.Data
import lombok.EqualsAndHashCode
import net.blueshell.api.base.BaseDTO
import net.blueshell.api.validation.survey.ValidAnswer

@Data
@EqualsAndHashCode(onlyExplicitlyIncluded = true, callSuper = true)
@Schema(name = "Answer")
@ValidAnswer
class AnswerDTO : BaseDTO() {
    private val id: Long? = null

    @NotNull
    private val questionId: @NotNull Long? = null

    private val optionSelections: MutableList<Boolean?>? = null

    private val textResponse: String? = null
}

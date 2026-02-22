package net.blueshell.api.domain.survey.web.dto.response

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import net.blueshell.api.shared.enums.QuestionType
import java.time.Instant

@Schema(name = "QuestionResponse")
data class QuestionResponse(
    var id: Long,

    @field:NotNull
    var idx: Long,

    @field:NotNull
    var surveyId: Long,

    @field:NotNull
    var type: QuestionType,

    @field:NotBlank(message = "Label cannot be empty.")
    @field:Size(max = 2055, message = "Label cannot exceed 2055 characters.")
    var label: String,

    var choiceLabels: MutableList<String>? = null,
    var version: Long,
    var createdAt: Instant,
    var updatedAt: Instant,
)

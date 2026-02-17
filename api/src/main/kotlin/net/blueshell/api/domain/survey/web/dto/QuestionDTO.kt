package net.blueshell.api.domain.survey.web.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import net.blueshell.api.domain.survey.web.validation.ValidQuestion
import net.blueshell.api.shared.enums.QuestionType
import java.time.Instant

@Schema(name = "Question")
@ValidQuestion
data class QuestionDTO(
    var id: Long? = null,

    @field:NotNull
    var idx: Long? = null,

    @field:NotNull
    var surveyId: Long? = null,

    @field:NotNull
    var type: QuestionType? = null,

    @field:NotBlank(message = "Label cannot be empty.")
    @field:Size(max = 2055, message = "Label cannot exceed 2055 characters.")
    var label: String? = null,

    var choiceLabels: MutableList<String>? = null,
    var version: Long? = null,
    var createdAt: Instant? = null,
    var updatedAt: Instant? = null,
)

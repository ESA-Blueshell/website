package net.blueshell.api.domain.survey.web.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotNull
import net.blueshell.api.domain.survey.web.validation.ValidAnswer
import java.time.Instant

@Schema(name = "Answer")
@ValidAnswer
data class AnswerDTO(
    var id: Long? = null,

    @field:NotNull
    var questionId: Long? = null,

    var optionSelections: MutableList<Boolean>? = null,

    var textResponse: String? = null,
    var version: Long? = null,
    var createdAt: Instant? = null,
    var updatedAt: Instant? = null,
)

package net.blueshell.api.domain.survey.web.dto.request

import io.swagger.v3.oas.annotations.media.Schema

@Schema(name = "AnswerRequest")
data class AnswerRequest(
    var questionId: Long,

    var optionSelections: MutableList<Boolean>? = null,

    var textResponse: String? = null
)

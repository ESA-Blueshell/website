package net.blueshell.api.domain.survey.command

import jakarta.validation.constraints.NotNull

/**
 * Answer information for commands.
 * Command-layer data structure (not a web DTO).
 */
data class AnswerData(
    @field:NotNull(message = "Question ID is required")
    val questionId: Long,

    val optionSelections: List<Boolean>? = null,
    val textResponse: String? = null,
    val version: Long? = null
)

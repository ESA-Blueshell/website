package net.blueshell.api.survey.api

import jakarta.validation.constraints.NotNull
import net.blueshell.api.survey.domain.ValidAnswer
import net.blueshell.api.survey.domain.AnswerCandidate

/**
 * Answer information for commands.
 * Command-layer data structure (not a web DTO).
 */
@ValidAnswer
data class AnswerData(
    @field:NotNull(message = "Question ID is required")
    override val questionId: Long,

    override val optionSelections: List<Boolean>? = null,
    override val textResponse: String? = null,
    val version: Long? = null
) : AnswerCandidate

package net.blueshell.api.domain.survey.application.event

import net.blueshell.api.shared.enums.QuestionType

data class QuestionChanged(
    val questionId: Long,
    val surveyId: Long,
    val type: QuestionType,
    val changeType: QuestionChange,
    val dirty: Boolean = false,
    val dirtyFields: Set<String> = emptySet(),
    val hasAnswers: Boolean = false
)

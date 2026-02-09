package net.blueshell.api.survey.application.event

import net.blueshell.api.shared.enums.QuestionType

data class QuestionChangedEvent(
    val questionId: Long,
    val surveyId: Long,
    val type: QuestionType,
    val changeType: QuestionChangeType,
    val dirty: Boolean = false,
    val dirtyFields: Set<String> = emptySet(),
    val hasAnswers: Boolean = false
)

package net.blueshell.api.survey.domain

interface AnswerCandidate {
    val questionId: Long
    val optionSelections: List<Boolean>?
    val textResponse: String?
}

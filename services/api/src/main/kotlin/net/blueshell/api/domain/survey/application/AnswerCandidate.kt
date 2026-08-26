package net.blueshell.api.domain.survey.application

interface AnswerCandidate {
    val questionId: Long
    val optionSelections: List<Boolean>?
    val textResponse: String?
}

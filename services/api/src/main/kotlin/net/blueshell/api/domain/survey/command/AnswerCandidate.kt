package net.blueshell.api.domain.survey.command

interface AnswerCandidate {
    val questionId: Long
    val optionSelections: List<Boolean>?
    val textResponse: String?
}

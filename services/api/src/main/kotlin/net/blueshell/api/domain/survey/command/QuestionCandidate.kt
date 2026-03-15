package net.blueshell.api.domain.survey.command

import net.blueshell.api.shared.enums.QuestionType

interface QuestionCandidate {
    val idx: Long
    val type: QuestionType
    val label: String
    val choiceLabels: List<String>?
}

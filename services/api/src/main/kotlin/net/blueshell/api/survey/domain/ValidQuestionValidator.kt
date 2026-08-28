package net.blueshell.api.survey.domain

import jakarta.validation.ConstraintValidator
import jakarta.validation.ConstraintValidatorContext
import net.blueshell.api.shared.enums.QuestionType

class ValidQuestionValidator : ConstraintValidator<ValidQuestion, QuestionCandidate> {
    override fun isValid(candidate: QuestionCandidate?, context: ConstraintValidatorContext?): Boolean {
        if (candidate == null) {
            return true
        }

        return when (candidate.type) {
            QuestionType.OPEN, QuestionType.DESCRIPTION -> candidate.choiceLabels.isNullOrEmpty()
            QuestionType.CHECKBOX, QuestionType.RADIO -> hasValidChoiceLabels(candidate.choiceLabels)
        }
    }

    private fun hasValidChoiceLabels(choiceLabels: List<String>?): Boolean =
        !choiceLabels.isNullOrEmpty() && choiceLabels.none { it.isBlank() || it.length > 100 }
}

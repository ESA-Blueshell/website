package net.blueshell.api.domain.survey.application.validation

import jakarta.validation.ConstraintValidator
import jakarta.validation.ConstraintValidatorContext
import net.blueshell.api.domain.survey.application.QuestionService
import net.blueshell.api.domain.survey.command.AnswerCandidate
import net.blueshell.api.domain.survey.persistence.Question
import net.blueshell.api.shared.enums.QuestionType
import org.springframework.stereotype.Component

@Component
class ValidAnswerValidator(
    private val questions: QuestionService,
) : ConstraintValidator<ValidAnswer, AnswerCandidate> {

    override fun isValid(candidate: AnswerCandidate?, context: ConstraintValidatorContext?): Boolean {
        if (candidate == null) {
            return true
        }

        val question = try {
            questions.findById(candidate.questionId)
        } catch (_: Exception) {
            return false
        }

        return when (question.type) {
            QuestionType.OPEN -> !candidate.textResponse.isNullOrBlank()
            QuestionType.CHECKBOX -> isValidCheckboxAnswer(candidate, question)
            QuestionType.RADIO -> isValidRadioAnswer(candidate, question)
            QuestionType.DESCRIPTION -> true
        }
    }

    private fun isValidCheckboxAnswer(candidate: AnswerCandidate, question: Question): Boolean {
        val selections = candidate.optionSelections ?: return false
        val choiceLabels = question.choiceLabels
        return !choiceLabels.isNullOrEmpty() && selections.size == choiceLabels.size
    }

    private fun isValidRadioAnswer(candidate: AnswerCandidate, question: Question): Boolean {
        val selections = candidate.optionSelections ?: return false
        val choiceLabels = question.choiceLabels

        if (!choiceLabels.isNullOrEmpty() && selections.size != choiceLabels.size) {
            return false
        }

        return selections.count { it } == 1
    }
}

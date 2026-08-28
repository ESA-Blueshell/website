package net.blueshell.api.survey.domain

import jakarta.validation.ConstraintValidator
import jakarta.validation.ConstraintValidatorContext
import net.blueshell.api.survey.api.QuestionService
import net.blueshell.api.survey.persistence.Question
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
            QuestionType.OPEN -> isValidOpenAnswer(candidate, question)
            QuestionType.CHECKBOX -> isValidCheckboxAnswer(candidate, question)
            QuestionType.RADIO -> isValidRadioAnswer(candidate, question)
            QuestionType.DESCRIPTION -> true
        }
    }

    private fun isValidOpenAnswer(candidate: AnswerCandidate, question: Question): Boolean {
        val text = candidate.textResponse
        if (question.required) return !text.isNullOrBlank()
        return true
    }

    private fun isValidCheckboxAnswer(candidate: AnswerCandidate, question: Question): Boolean {
        val choiceLabels = question.choiceLabels
        if (choiceLabels.isNullOrEmpty()) return false
        val selections = candidate.optionSelections ?: return !question.required
        if (selections.size != choiceLabels.size) return false
        if (question.required && selections.none { it }) return false
        return true
    }

    private fun isValidRadioAnswer(candidate: AnswerCandidate, question: Question): Boolean {
        val choiceLabels = question.choiceLabels
        if (choiceLabels.isNullOrEmpty()) return false
        val selections = candidate.optionSelections
            ?: return !question.required
        if (selections.size != choiceLabels.size) return false
        val chosen = selections.count { it }
        if (chosen > 1) return false
        if (question.required && chosen == 0) return false
        return true
    }
}

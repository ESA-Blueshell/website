package net.blueshell.api.survey.api.validation

import jakarta.validation.ConstraintValidator
import jakarta.validation.ConstraintValidatorContext
import net.blueshell.api.shared.enums.QuestionType
import net.blueshell.api.survey.api.dto.AnswerDTO
import net.blueshell.api.survey.domain.model.Question
import net.blueshell.api.survey.persistence.QuestionRepository
import org.springframework.beans.factory.annotation.Autowired


data class ValidAnswerValidator @Autowired constructor(val questions: QuestionRepository) :
    ConstraintValidator<ValidAnswer, AnswerDTO> {
    override fun isValid(dto: AnswerDTO?, context: ConstraintValidatorContext?): Boolean {
        if (dto == null || dto.questionId == null) {
            return true // Let @NotNull handle this
        }

        val question = questions.findById(dto.questionId).orElse(null) ?: return false

        return when (question.type) {
            QuestionType.OPEN -> dto.textResponse != null && !dto.textResponse!!.trim { it <= ' ' }.isEmpty()
            QuestionType.CHECKBOX -> isValidCheckboxAnswer(dto, question)
            QuestionType.RADIO -> isValidRadioAnswer(dto, question)
            QuestionType.DESCRIPTION -> true
            else -> false
        }
    }

    private fun isValidCheckboxAnswer(dto: AnswerDTO, question: Question): Boolean {
        val selections = dto.optionSelections
        val choiceLabels = question.choiceLabels

        if (selections == null) {
            return false
        }

        return !choiceLabels.isNullOrEmpty() && selections.size == choiceLabels.size
    }

    private fun isValidRadioAnswer(dto: AnswerDTO, question: Question): Boolean {
        val selections = dto.optionSelections
        val choiceLabels = question.choiceLabels

        if (selections == null || (!choiceLabels.isNullOrEmpty() && selections.size != choiceLabels.size)) {
            return false
        }

        val trueCount = selections.stream().filter { it == true }.count()
        return trueCount == 1L
    }
}

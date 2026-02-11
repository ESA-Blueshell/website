package net.blueshell.api.domain.survey.web.validation

import jakarta.validation.ConstraintValidator
import jakarta.validation.ConstraintValidatorContext
import net.blueshell.api.shared.enums.QuestionType
import net.blueshell.api.survey.web.dto.QuestionDTO
import org.springframework.beans.factory.annotation.Autowired

class ValidQuestionValidator @Autowired constructor() :
    ConstraintValidator<ValidQuestion?, QuestionDTO?> {
    override fun isValid(dto: QuestionDTO?, context: ConstraintValidatorContext?): Boolean {
        if (dto == null) {
            return true // Let @NotNull handle this
        }

        return when (dto.type) {
            QuestionType.OPEN, QuestionType.DESCRIPTION -> dto.choiceLabels.isNullOrEmpty()
            QuestionType.CHECKBOX, QuestionType.RADIO -> hasValidChoiceLabels(dto.choiceLabels)
            else -> false
        }
    }

    private fun hasValidChoiceLabels(choiceLabels: MutableList<String>?): Boolean {
        return !choiceLabels.isNullOrEmpty() &&
                choiceLabels.stream()
                    .noneMatch { label: String -> label.trim { it <= ' ' }.isEmpty() }
    }
}

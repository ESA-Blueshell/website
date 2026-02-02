package net.blueshell.api.validation.survey

import jakarta.validation.ConstraintValidator
import jakarta.validation.ConstraintValidatorContext
import net.blueshell.api.common.enums.QuestionType
import net.blueshell.api.dto.survey.QuestionDTO
import org.springframework.beans.factory.annotation.Autowired

class ValidQuestionValidator @Autowired constructor() : ConstraintValidator<ValidQuestion?, QuestionDTO?> {
    override fun isValid(dto: QuestionDTO?, context: ConstraintValidatorContext?): Boolean {
        if (dto == null || dto.getType() == null) {
            return true // Let @NotNull handle this
        }

        return when (dto.getType()) {
            QuestionType.OPEN, QuestionType.DESCRIPTION -> dto.getChoiceLabels() == null || dto.getChoiceLabels()
                .isEmpty()

            QuestionType.CHECKBOX, QuestionType.RADIO -> hasValidChoiceLabels(dto.getChoiceLabels())
            else -> false
        }
    }

    private fun hasValidChoiceLabels(choiceLabels: MutableList<String?>?): Boolean {
        return choiceLabels != null && !choiceLabels.isEmpty() &&
                choiceLabels.stream()
                    .noneMatch { label: String? -> label == null || label.trim { it <= ' ' }.isEmpty() }
    }
}
package net.blueshell.api.validation.survey

import jakarta.validation.ConstraintValidator
import jakarta.validation.ConstraintValidatorContext
import net.blueshell.api.dto.survey.QuestionDTO

class ValidQuestionListValidator : ConstraintValidator<ValidQuestionList?, MutableList<QuestionDTO?>?> {
    override fun isValid(questions: MutableList<QuestionDTO>?, context: ConstraintValidatorContext): Boolean {
        if (questions == null || questions.isEmpty()) {
            // handled by @NotEmpty
            return true
        }

        val seenIdx: MutableSet<Long?> = HashSet<Long?>()
        for (q in questions) {
            if (q.getIdx() == null) return false
            if (!seenIdx.add(q.getIdx())) {
                context.disableDefaultConstraintViolation()
                context.buildConstraintViolationWithTemplate("Duplicate question index: " + q.getIdx())
                    .addConstraintViolation()
                return false
            }
        }

        return true
    }
}


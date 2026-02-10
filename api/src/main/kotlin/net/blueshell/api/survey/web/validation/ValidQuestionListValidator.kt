package net.blueshell.api.survey.web.validation

import jakarta.validation.ConstraintValidator
import jakarta.validation.ConstraintValidatorContext
import net.blueshell.api.survey.web.dto.QuestionDTO

class ValidQuestionListValidator :
    ConstraintValidator<ValidQuestionList, MutableList<QuestionDTO>> {
    override fun isValid(questions: MutableList<QuestionDTO>?, context: ConstraintValidatorContext): Boolean {
        if (questions.isNullOrEmpty()) {
            // handled by @NotEmpty
            return true
        }

        val seenIdx: MutableSet<Long> = HashSet()
        for (q in questions) {
            if (!seenIdx.add(q.idx!!)) {
                context.disableDefaultConstraintViolation()
                context.buildConstraintViolationWithTemplate("Duplicate question index: " + q.idx)
                    .addConstraintViolation()
                return false
            }
        }

        return true
    }
}

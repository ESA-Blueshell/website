package net.blueshell.api.survey.web.validation

import jakarta.validation.ConstraintValidator
import jakarta.validation.ConstraintValidatorContext
import net.blueshell.api.survey.web.dto.AnswerDTO

class ValidAnswerListValidator : ConstraintValidator<ValidAnswerList, MutableList<AnswerDTO>> {
    override fun isValid(answers: MutableList<AnswerDTO>?, context: ConstraintValidatorContext): Boolean {
        if (answers == null) {
            return true // Let @NotNull handle if required
        }

        val seenQuestionIds: MutableSet<Long> = HashSet()
        for (a in answers) {
            if (!seenQuestionIds.add(a.questionId)) {
                context.disableDefaultConstraintViolation()
                context.buildConstraintViolationWithTemplate("Duplicate answers for question ID: " + a.questionId)
                    .addConstraintViolation()
                return false
            }
        }

        return true
    }
}

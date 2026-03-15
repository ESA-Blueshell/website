package net.blueshell.api.domain.survey.application.validation

import jakarta.validation.ConstraintValidator
import jakarta.validation.ConstraintValidatorContext
import net.blueshell.api.domain.survey.command.QuestionCandidate

class ValidQuestionListValidator : ConstraintValidator<ValidQuestionList, List<QuestionCandidate>> {
    override fun isValid(questions: List<QuestionCandidate>?, context: ConstraintValidatorContext): Boolean {
        if (questions.isNullOrEmpty()) {
            return true
        }

        val seenIdx = mutableSetOf<Long>()
        questions.forEachIndexed { index, question ->
            if (!seenIdx.add(question.idx)) {
                context.disableDefaultConstraintViolation()
                context
                    .buildConstraintViolationWithTemplate("Duplicate question index: ${question.idx}")
                    .addPropertyNode("questions")
                    .inIterable().atIndex(index)
                    .addConstraintViolation()
                return false
            }
        }

        return true
    }
}

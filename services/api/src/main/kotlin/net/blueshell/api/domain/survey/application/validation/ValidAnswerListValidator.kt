package net.blueshell.api.domain.survey.application.validation

import jakarta.validation.ConstraintValidator
import jakarta.validation.ConstraintValidatorContext
import net.blueshell.api.domain.survey.application.AnswerCandidate

class ValidAnswerListValidator : ConstraintValidator<ValidAnswerList, List<AnswerCandidate>> {
    override fun isValid(answers: List<AnswerCandidate>?, context: ConstraintValidatorContext): Boolean {
        if (answers == null) {
            return true
        }

        val seenQuestionIds = mutableSetOf<Long>()
        answers.forEachIndexed { index, answer ->
            if (!seenQuestionIds.add(answer.questionId)) {
                context.disableDefaultConstraintViolation()
                context
                    .buildConstraintViolationWithTemplate("Duplicate answer for question ID: ${answer.questionId}")
                    .addPropertyNode("answers")
                    .inIterable().atIndex(index)
                    .addConstraintViolation()
                return false
            }
        }

        return true
    }
}

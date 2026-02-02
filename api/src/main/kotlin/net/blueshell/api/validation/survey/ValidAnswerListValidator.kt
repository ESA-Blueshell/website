package net.blueshell.api.validation.survey

import jakarta.validation.ConstraintValidator
import jakarta.validation.ConstraintValidatorContext
import net.blueshell.api.dto.survey.AnswerDTO
import net.blueshell.api.service.survey.SurveyService
import org.springframework.beans.factory.annotation.Autowired

class ValidAnswerListValidator : ConstraintValidator<ValidAnswerList?, MutableList<AnswerDTO?>?> {
    @Autowired
    private val surveys: SurveyService? = null

    override fun isValid(answers: MutableList<AnswerDTO>?, context: ConstraintValidatorContext): Boolean {
        if (answers == null) {
            return true // Let @NotNull handle if required
        }

        val seenQuestionIds: MutableSet<Long?> = HashSet<Long?>()
        for (a in answers) {
            if (a.getQuestionId() == null) return false
            if (!seenQuestionIds.add(a.getQuestionId())) {
                context.disableDefaultConstraintViolation()
                context.buildConstraintViolationWithTemplate("Duplicate answers for question ID: " + a.getQuestionId())
                    .addConstraintViolation()
                return false
            }
        }

        return true
    }
}

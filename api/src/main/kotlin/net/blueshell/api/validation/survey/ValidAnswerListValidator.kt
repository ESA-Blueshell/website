package net.blueshell.api.validation.survey;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import net.blueshell.api.dto.survey.AnswerDTO;
import net.blueshell.api.service.survey.SurveyService;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ValidAnswerListValidator implements ConstraintValidator<ValidAnswerList, List<AnswerDTO>> {

    @Autowired
    private SurveyService surveys;

    @Override
    public boolean isValid(List<AnswerDTO> answers, ConstraintValidatorContext context) {
        if (answers == null) {
            return true; // Let @NotNull handle if required
        }

        Set<Long> seenQuestionIds = new HashSet<>();
        for (AnswerDTO a : answers) {
            if (a.getQuestionId() == null) return false;
            if (!seenQuestionIds.add(a.getQuestionId())) {
                context.disableDefaultConstraintViolation();
                context.buildConstraintViolationWithTemplate("Duplicate answers for question ID: " + a.getQuestionId())
                        .addConstraintViolation();
                return false;
            }
        }

        return true;
    }
}

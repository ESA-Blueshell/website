package net.blueshell.api.validation.survey;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import net.blueshell.api.dto.survey.QuestionDTO;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ValidQuestionListValidator implements ConstraintValidator<ValidQuestionList, List<QuestionDTO>> {

    @Override
    public boolean isValid(List<QuestionDTO> questions, ConstraintValidatorContext context) {
        if (questions == null || questions.isEmpty()) {
            // handled by @NotEmpty
            return true;
        }

        Set<Long> seenIdx = new HashSet<>();
        for (QuestionDTO q : questions) {
            if (q.getIdx() == null) return false;
            if (!seenIdx.add(q.getIdx())) {
                context.disableDefaultConstraintViolation();
                context.buildConstraintViolationWithTemplate("Duplicate question index: " + q.getIdx())
                        .addConstraintViolation();
                return false;
            }
        }

        return true;
    }
}


package net.blueshell.api.validation.survey;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import net.blueshell.api.dto.survey.QuestionDTO;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

public class ValidQuestionValidator implements ConstraintValidator<ValidQuestion, QuestionDTO> {

    @Autowired
    public ValidQuestionValidator() {
    }

    @Override
    public boolean isValid(QuestionDTO dto, ConstraintValidatorContext context) {
        if (dto == null || dto.getType() == null) {
            return true; // Let @NotNull handle this
        }

        return switch (dto.getType()) {
            case OPEN, DESCRIPTION -> dto.getChoiceLabels() == null || dto.getChoiceLabels().isEmpty();
            case CHECKBOX, RADIO -> hasValidChoiceLabels(dto.getChoiceLabels());
            default -> false;
        };
    }

    private boolean hasValidChoiceLabels(List<String> choiceLabels) {
        return choiceLabels != null &&
                !choiceLabels.isEmpty() &&
                choiceLabels.stream().noneMatch(label -> label == null || label.trim().isEmpty());
    }
}
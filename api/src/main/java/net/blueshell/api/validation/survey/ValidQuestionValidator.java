package net.blueshell.api.validation.survey;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import net.blueshell.api.common.enums.QuestionType;
import net.blueshell.api.dto.survey.QuestionDTO;
import org.springframework.beans.factory.annotation.Autowired;

public class ValidQuestionValidator implements ConstraintValidator<ValidQuestion, QuestionDTO> {

    @Autowired
    public ValidQuestionValidator() {
    }

    @Override
    public boolean isValid(QuestionDTO dto, ConstraintValidatorContext context) {
        if (dto.getLabel() == null) {
            // Let @NotNull handle this
            return true;
        }

        return switch (dto.getType()) {
            // If a question is an open question, don't allow choice labels to be set
            case QuestionType.OPEN -> dto.getChoiceLabels() == null
                    || dto.getChoiceLabels().isEmpty();
            // If a question is checkbox or radio, ensure that choice labels are set
            // And that all the choice labels have some text
            case QuestionType.CHECKBOX, QuestionType.RADIO -> !dto.getChoiceLabels().isEmpty()
                    && dto.getChoiceLabels().stream().noneMatch(String::isEmpty);
            default -> false;
        };
    }
}
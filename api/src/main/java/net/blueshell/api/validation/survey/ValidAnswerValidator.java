package net.blueshell.api.validation.survey;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import net.blueshell.api.common.enums.QuestionType;
import net.blueshell.api.dto.survey.AnswerDTO;
import net.blueshell.api.repository.survey.QuestionRepository;
import org.springframework.beans.factory.annotation.Autowired;

public class ValidAnswerValidator implements ConstraintValidator<ValidAnswer, AnswerDTO> {


    private final QuestionRepository questions;

    @Autowired
    public ValidAnswerValidator(QuestionRepository questions) {
        this.questions = questions;
    }

    @Override
    public boolean isValid(AnswerDTO dto, ConstraintValidatorContext context) {
        if (dto.getQuestionId() == null) {
            // Let @NotNull handle this
            return true;
        }

        var question = questions.findById(dto.getQuestionId()).orElse(null);
        if (question == null) {
            return false;
        }

        return switch (question.getType()) {
            case QuestionType.OPEN -> !dto.getTextResponse().isEmpty();
            case QuestionType.CHECKBOX -> dto.getOptionSelections() != null && dto.getOptionSelections().size() == question.getChoiceLabels().size();
            case QuestionType.RADIO -> {
                if (dto.getOptionSelections() == null) {
                    yield false;
                }
                if (dto.getOptionSelections().size() != question.getChoiceLabels().size()) {
                    yield false;
                }
                yield dto.getOptionSelections().stream().filter(b -> b).count() == 1;
            }
            default -> false;
        };
    }
}
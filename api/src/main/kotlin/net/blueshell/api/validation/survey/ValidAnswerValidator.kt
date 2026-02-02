package net.blueshell.api.validation.survey;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import net.blueshell.api.dto.survey.AnswerDTO;
import net.blueshell.api.model.survey.Question;
import net.blueshell.api.repository.survey.QuestionRepository;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

public record ValidAnswerValidator(
        QuestionRepository questions) implements ConstraintValidator<ValidAnswer, AnswerDTO> {

    @Autowired
    public ValidAnswerValidator {
    }

    @Override
    public boolean isValid(AnswerDTO dto, ConstraintValidatorContext context) {
        if (dto == null || dto.getQuestionId() == null) {
            return true; // Let @NotNull handle this
        }

        var question = questions.findById(dto.getQuestionId()).orElse(null);
        if (question == null) {
            return false;
        }

        return switch (question.getType()) {
            case OPEN -> dto.getTextResponse() != null && !dto.getTextResponse().trim().isEmpty();
            case CHECKBOX -> isValidCheckboxAnswer(dto, question);
            case RADIO -> isValidRadioAnswer(dto, question);
            case DESCRIPTION -> true; // Description questions don't require answers
            default -> false;
        };
    }

    private boolean isValidCheckboxAnswer(AnswerDTO dto, Question question) {
        List<Boolean> selections = dto.getOptionSelections();
        List<String> choiceLabels = question.getChoiceLabels();

        if (selections == null || choiceLabels == null) {
            return false;
        }

        return selections.size() == choiceLabels.size();
    }

    private boolean isValidRadioAnswer(AnswerDTO dto, Question question) {
        List<Boolean> selections = dto.getOptionSelections();
        List<String> choiceLabels = question.getChoiceLabels();

        if (selections == null || choiceLabels == null || selections.size() != choiceLabels.size()) {
            return false;
        }

        long trueCount = selections.stream().filter(Boolean::booleanValue).count();
        return trueCount == 1;
    }
}
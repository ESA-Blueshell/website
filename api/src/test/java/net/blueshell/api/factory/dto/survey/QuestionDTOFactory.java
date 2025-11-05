package net.blueshell.api.factory.dto.survey;

import lombok.RequiredArgsConstructor;
import net.blueshell.api.common.enums.QuestionType;
import net.blueshell.api.dto.survey.QuestionDTO;
import net.blueshell.api.factory.dto.BaseDtoFactory;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Factory for QuestionDTO test instances.
 */
@Component
@RequiredArgsConstructor
public class QuestionDTOFactory extends BaseDtoFactory<QuestionDTO> {

    @Override
    public Class<QuestionDTO> targetType() {
        return QuestionDTO.class;
    }

    @Override
    public QuestionDTO createBasic() {
        return createByType(QuestionType.OPEN);
    }

    public QuestionDTO createByType(QuestionType type) {
        QuestionDTO dto = new QuestionDTO();
        dto.setIdx(nextId());
        dto.setSurveyId(nextId());
        dto.setType(type);

        switch (type) {
            case OPEN -> {
                dto.setLabel("What are your thoughts?");
                dto.setChoiceLabels(null);
            }
            case RADIO -> {
                dto.setLabel("Please select one option:");
                dto.setChoiceLabels(List.of("Option A", "Option B", "Option C", "Option D"));
            }
            case CHECKBOX -> {
                dto.setLabel("Select all that apply:");
                dto.setChoiceLabels(List.of("Choice 1", "Choice 2", "Choice 3", "Choice 4", "Choice 5"));
            }
            case DESCRIPTION -> {
                dto.setLabel("Important information:");
                dto.setChoiceLabels(null);
            }
            default -> {
                dto.setLabel("Question " + nextId());
                dto.setChoiceLabels(null);
            }
        }
        return dto;
    }

    public QuestionDTO createOpen() { return createByType(QuestionType.OPEN); }
    public QuestionDTO createRadio() { return createByType(QuestionType.RADIO); }
    public QuestionDTO createCheckbox() { return createByType(QuestionType.CHECKBOX); }
    public QuestionDTO createDescription() { return createByType(QuestionType.DESCRIPTION); }
}

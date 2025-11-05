package net.blueshell.api.factory.dto.survey;

import lombok.RequiredArgsConstructor;
import net.blueshell.api.dto.survey.AnswerDTO;
import net.blueshell.api.dto.survey.QuestionDTO;
import net.blueshell.api.factory.dto.BaseDtoFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Factory for AnswerDTO test instances.
 */
@Component
@RequiredArgsConstructor
public class AnswerDTOFactory extends BaseDtoFactory<AnswerDTO> {

    @Override
    public Class<AnswerDTO> targetType() {
        return AnswerDTO.class;
    }

    @Override
    public AnswerDTO createBasic() {
        AnswerDTO dto = new AnswerDTO();
        dto.setQuestionId(nextId());
        dto.setTextResponse("Sample text response");
        dto.setOptionSelections(null);
        return dto;
    }

    public AnswerDTO createForQuestion(QuestionDTO question) {
        AnswerDTO dto = new AnswerDTO();
        dto.setQuestionId(question.getId() != null ? question.getId() : nextId());

        switch (question.getType()) {
            case OPEN -> {
                dto.setTextResponse("This is a text response for the open question");
                dto.setOptionSelections(null);
            }
            case RADIO -> {
                dto.setTextResponse(null);
                if (question.getChoiceLabels() != null && !question.getChoiceLabels().isEmpty()) {
                    List<Boolean> selections = new ArrayList<>();
                    for (int i = 0; i < question.getChoiceLabels().size(); i++) {
                        selections.add(i == 0);
                    }
                    dto.setOptionSelections(selections);
                }
            }
            case CHECKBOX -> {
                dto.setTextResponse(null);
                if (question.getChoiceLabels() != null && !question.getChoiceLabels().isEmpty()) {
                    List<Boolean> selections = new ArrayList<>();
                    for (int i = 0; i < question.getChoiceLabels().size(); i++) {
                        selections.add(random.nextBoolean());
                    }
                    dto.setOptionSelections(selections);
                }
            }
            case DESCRIPTION -> {
                dto.setTextResponse(null);
                dto.setOptionSelections(null);
            }
            default -> {
                dto.setTextResponse("Default response");
                dto.setOptionSelections(null);
            }
        }
        return dto;
    }

    public AnswerDTO createForOpenQuestion() {
        AnswerDTO dto = new AnswerDTO();
        dto.setQuestionId(nextId());
        dto.setTextResponse("Detailed response to the open-ended question");
        dto.setOptionSelections(null);
        return dto;
    }

    public AnswerDTO createForRadioQuestion(int optionCount, int selectedIndex) {
        AnswerDTO dto = new AnswerDTO();
        dto.setQuestionId(nextId());
        dto.setTextResponse(null);

        List<Boolean> selections = new ArrayList<>();
        for (int i = 0; i < optionCount; i++) {
            selections.add(i == selectedIndex);
        }
        dto.setOptionSelections(selections);
        return dto;
    }

    public AnswerDTO createForCheckboxQuestion(int optionCount, List<Integer> selectedIndices) {
        AnswerDTO dto = new AnswerDTO();
        dto.setQuestionId(nextId());
        dto.setTextResponse(null);

        List<Boolean> selections = new ArrayList<>();
        for (int i = 0; i < optionCount; i++) {
            selections.add(selectedIndices.contains(i));
        }
        dto.setOptionSelections(selections);
        return dto;
    }
}

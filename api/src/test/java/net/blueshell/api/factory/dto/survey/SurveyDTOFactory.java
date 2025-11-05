package net.blueshell.api.factory.dto.survey;

import lombok.RequiredArgsConstructor;
import net.blueshell.api.common.enums.QuestionType;
import net.blueshell.api.dto.survey.QuestionDTO;
import net.blueshell.api.dto.survey.SurveyDTO;
import net.blueshell.api.factory.dto.BaseDtoFactory;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Factory for SurveyDTO test instances.
 */
@Component
@RequiredArgsConstructor
public class SurveyDTOFactory extends BaseDtoFactory<SurveyDTO> {

    private final QuestionDTOFactory questionFactory;

    @Override
    public Class<SurveyDTO> targetType() {
        return SurveyDTO.class;
    }

    @Override
    public SurveyDTO createBasic() {
        return createWithQuestionTypes(QuestionType.OPEN, QuestionType.RADIO);
    }

    public SurveyDTO createWithQuestionTypes(QuestionType... questionTypes) {
        SurveyDTO dto = new SurveyDTO();

        List<QuestionDTO> questions = Arrays.stream(questionTypes)
                .map(questionFactory::createByType)
                .collect(Collectors.toList());

        for (int i = 0; i < questions.size(); i++) {
            questions.get(i).setIdx((long) (i + 1));
        }

        dto.setQuestions(questions);
        dto.setResponseCount(0L);
        return dto;
    }

    public SurveyDTO createWithOpenQuestions(int count) {
        QuestionType[] types = new QuestionType[count];
        Arrays.fill(types, QuestionType.OPEN);
        return createWithQuestionTypes(types);
    }

    public SurveyDTO createWithMixedQuestions() {
        return createWithQuestionTypes(
                QuestionType.DESCRIPTION, QuestionType.RADIO, QuestionType.CHECKBOX, QuestionType.OPEN
        );
    }

    public SurveyDTO createWithMultipleChoiceOnly() {
        return createWithQuestionTypes(
                QuestionType.RADIO, QuestionType.RADIO, QuestionType.CHECKBOX
        );
    }
}

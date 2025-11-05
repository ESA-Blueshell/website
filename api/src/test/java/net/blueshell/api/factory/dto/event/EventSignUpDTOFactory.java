package net.blueshell.api.factory.dto.event;

import lombok.RequiredArgsConstructor;
import net.blueshell.api.common.enums.QuestionType;
import net.blueshell.api.dto.event.EventSignUpDTO;
import net.blueshell.api.dto.survey.AnswerDTO;
import net.blueshell.api.dto.survey.SurveyDTO;
import net.blueshell.api.dto.user.SimpleUserDTO;
import net.blueshell.api.factory.dto.BaseDtoFactory;
import net.blueshell.api.factory.dto.survey.AnswerDTOFactory;
import net.blueshell.api.factory.dto.survey.SurveyDTOFactory;
import net.blueshell.api.factory.dto.user.SimpleUserDTOFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Factory for EventSignUpDTO test instances.
 */
@Component
@RequiredArgsConstructor
public class EventSignUpDTOFactory extends BaseDtoFactory<EventSignUpDTO> {

    private final AnswerDTOFactory answerFactory;
    private final SimpleUserDTOFactory userFactory;
    private final SurveyDTOFactory surveyFactory;

    @Override
    public Class<EventSignUpDTO> targetType() {
        return EventSignUpDTO.class;
    }

    @Override
    public EventSignUpDTO createBasic() {
        EventSignUpDTO dto = new EventSignUpDTO();
        dto.setEventId(nextId());
        SimpleUserDTO user = userFactory.createBasic();
        dto.setUser(user);
        dto.setUserId(user.getId());
        dto.setGuest(null);

        SurveyDTO survey = surveyFactory.createBasic();
        List<AnswerDTO> answers = survey.getQuestions().stream()
                .map(answerFactory::createForQuestion)
                .collect(Collectors.toList());
        dto.setAnswers(answers);

        return dto;
    }

    public EventSignUpDTO createForSurvey(SurveyDTO survey) {
        EventSignUpDTO dto = new EventSignUpDTO();
        dto.setEventId(nextId());
        SimpleUserDTO user = userFactory.createBasic();
        dto.setUser(user);
        dto.setUserId(user.getId());
        dto.setGuest(null);

        List<AnswerDTO> answers = survey.getQuestions().stream()
                .map(answerFactory::createForQuestion)
                .collect(Collectors.toList());
        dto.setAnswers(answers);

        return dto;
    }

    public EventSignUpDTO createWithQuestionTypes(QuestionType... questionTypes) {
        SurveyDTO survey = surveyFactory.createWithQuestionTypes(questionTypes);
        return createForSurvey(survey);
    }
}

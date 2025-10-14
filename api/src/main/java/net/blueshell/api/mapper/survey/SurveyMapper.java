package net.blueshell.api.mapper.survey;

import lombok.extern.slf4j.Slf4j;
import net.blueshell.api.base.BaseMapper;
import net.blueshell.api.dto.survey.SurveyDTO;
import net.blueshell.api.model.survey.Survey;
import org.mapstruct.*;

@Slf4j
@Mapper(componentModel = "spring", uses = {QuestionMapper.class})
public abstract class SurveyMapper extends BaseMapper<Survey, SurveyDTO> {
    @Mapping(target = "id")
    @Mapping(target = "questions")
    @BeanMapping(ignoreByDefault = true)
    public abstract Survey fromDTO(SurveyDTO dto);

    @Mapping(target = "id")
    @Mapping(target = "questions")
    @Mapping(target = "responseCount")
    @BeanMapping(ignoreByDefault = true)
    public abstract SurveyDTO toDTO(Survey survey);

    @AfterMapping
    protected void linkQuestions(@MappingTarget Survey survey) {
        if (survey.getQuestions() != null) {
            survey.getQuestions().forEach(q -> q.setSurvey(survey));
        }
    }
}

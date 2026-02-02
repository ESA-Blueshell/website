package net.blueshell.api.mapper.survey

import lombok.extern.slf4j.Slf4j
import net.blueshell.api.base.BaseMapper
import net.blueshell.api.dto.survey.SurveyDTO
import net.blueshell.api.model.survey.Question
import net.blueshell.api.model.survey.Survey
import org.mapstruct.*
import java.util.function.Consumer

@Slf4j
@Mapper(componentModel = "spring", uses = [QuestionMapper::class])
abstract class SurveyMapper : BaseMapper<Survey?, SurveyDTO?>() {
    @Mapping(target = "id")
    @Mapping(target = "questions")
    @Mapping(target = "version")
    @BeanMapping(ignoreByDefault = true)
    abstract override fun fromDTO(dto: SurveyDTO?): Survey?

    @Mapping(target = "id")
    @Mapping(target = "questions")
    @Mapping(target = "responseCount")
    @Mapping(target = "version")
    @BeanMapping(ignoreByDefault = true)
    abstract override fun toDTO(survey: Survey?): SurveyDTO?

    @AfterMapping
    protected fun linkQuestions(@MappingTarget survey: Survey) {
        if (survey.getQuestions() != null) {
            survey.getQuestions().forEach(Consumer { q: Question? -> q!!.setSurvey(survey) })
        }
    }
}

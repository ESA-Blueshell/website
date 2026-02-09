package net.blueshell.api.survey.web.mapper

import net.blueshell.api.survey.web.dto.SurveyDTO
import net.blueshell.api.shared.mapper.BaseMapper
import net.blueshell.api.survey.persistence.Survey
import org.mapstruct.BeanMapping
import org.mapstruct.Mapper
import org.mapstruct.Mapping

@Mapper(componentModel = "spring", uses = [QuestionMapper::class])
abstract class SurveyMapper : BaseMapper<Survey, SurveyDTO>() {
    @Mapping(target = "questions")
    @Mapping(target = "version")
    @BeanMapping(ignoreByDefault = true)
    abstract override fun fromDTO(dto: SurveyDTO): Survey

    @Mapping(target = "id")
    @Mapping(target = "questions")
    @Mapping(target = "responseCount")
    @Mapping(target = "version")
    @BeanMapping(ignoreByDefault = true)
    abstract override fun toDTO(survey: Survey): SurveyDTO
}

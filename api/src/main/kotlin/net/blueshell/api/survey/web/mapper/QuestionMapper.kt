package net.blueshell.api.survey.web.mapper

import net.blueshell.api.survey.web.dto.QuestionDTO
import net.blueshell.api.shared.mapper.BaseMapper
import net.blueshell.api.survey.persistence.Question
import org.mapstruct.*

@Mapper(componentModel = "spring")
abstract class QuestionMapper : BaseMapper<Question, QuestionDTO>() {
    @Mapping(target = "label")
    @Mapping(target = "type")
    @Mapping(target = "idx")
    @Mapping(target = "surveyId")
    @Mapping(target = "choiceLabels")
    @Mapping(target = "version")
    @BeanMapping(ignoreByDefault = true, nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    abstract fun fromDTO(dto: QuestionDTO, @MappingTarget question: Question): Question

    @Mapping(target = "id")
    @Mapping(target = "label")
    @Mapping(target = "type")
    @Mapping(target = "idx")
    @Mapping(target = "surveyId")
    @Mapping(target = "choiceLabels")
    @Mapping(target = "version")
    @BeanMapping(ignoreByDefault = true)
    abstract override fun toDTO(question: Question): QuestionDTO
}

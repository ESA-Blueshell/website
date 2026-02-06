package net.blueshell.api.mapper.survey

import net.blueshell.api.mapper.base.BaseMapper
import net.blueshell.api.dto.survey.QuestionDTO
import net.blueshell.api.model.survey.Question
import org.mapstruct.BeanMapping
import org.mapstruct.Mapper
import org.mapstruct.Mapping
import org.mapstruct.MappingTarget
import org.mapstruct.NullValuePropertyMappingStrategy

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

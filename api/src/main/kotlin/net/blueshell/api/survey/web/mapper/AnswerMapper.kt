package net.blueshell.api.survey.web.mapper

import net.blueshell.api.survey.web.dto.AnswerDTO
import net.blueshell.api.shared.mapper.BaseMapper
import net.blueshell.api.survey.persistence.Answer
import org.mapstruct.BeanMapping
import org.mapstruct.Mapper
import org.mapstruct.Mapping
import org.mapstruct.MappingTarget

@Mapper(componentModel = "spring")
abstract class AnswerMapper : BaseMapper<Answer, AnswerDTO>() {
    @Mapping(target = "questionId")
    @Mapping(target = "optionSelections")
    @Mapping(target = "textResponse")
    @Mapping(target = "version")
    @BeanMapping(ignoreByDefault = true)
    abstract fun fromDTO(dto: AnswerDTO, @MappingTarget answer: Answer): Answer

    @Mapping(target = "id")
    @Mapping(target = "questionId")
    @Mapping(target = "optionSelections")
    @Mapping(target = "textResponse")
    @Mapping(target = "version")
    @BeanMapping(ignoreByDefault = true)
    abstract override fun toDTO(answer: Answer): AnswerDTO
}

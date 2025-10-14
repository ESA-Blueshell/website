package net.blueshell.api.mapper.survey;

import lombok.extern.slf4j.Slf4j;
import net.blueshell.api.base.BaseMapper;
import net.blueshell.api.dto.survey.QuestionDTO;
import net.blueshell.api.model.survey.Question;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Slf4j
@Mapper(componentModel = "spring")
public abstract class QuestionMapper extends BaseMapper<Question, QuestionDTO> {
    @Mapping(target = "id")
    @Mapping(target = "label")
    @Mapping(target = "type")
    @Mapping(target = "idx")
    @Mapping(target = "choiceLabels")
    @BeanMapping(ignoreByDefault = true)
    public abstract Question fromDTO(QuestionDTO dto, @MappingTarget Question question);

    @Mapping(target = "id")
    @Mapping(target = "label")
    @Mapping(target = "type")
    @Mapping(target = "idx")
    @Mapping(target = "choiceLabels")
    @BeanMapping(ignoreByDefault = true)
    public abstract QuestionDTO toDTO(Question question);
}

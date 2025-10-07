package net.blueshell.api.mapper.survey;

import lombok.extern.slf4j.Slf4j;
import net.blueshell.api.base.BaseMapper;
import net.blueshell.api.dto.survey.AnswerDTO;
import net.blueshell.api.model.survey.Answer;
import org.mapstruct.*;

@Slf4j
@Mapper(componentModel = "spring")
public abstract class AnswerMapper extends BaseMapper<Answer, AnswerDTO> {
    @Mapping(target = "id")
    @Mapping(target = "questionId")
    @Mapping(target = "optionSelections")
    @Mapping(target = "textResponse")
    @BeanMapping(ignoreByDefault = true)
    public abstract Answer fromDTO(AnswerDTO dto, @MappingTarget Answer answer);

    @Mapping(target = "id")
    @Mapping(target = "questionId")
    @Mapping(target = "optionSelections")
    @Mapping(target = "textResponse")
    @BeanMapping(ignoreByDefault = true)
    public abstract AnswerDTO toDTO(Answer answer);
}

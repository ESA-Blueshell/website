package net.blueshell.api.mapper;

import lombok.extern.slf4j.Slf4j;
import net.blueshell.api.base.BaseMapper;
import net.blueshell.api.dto.FormQuestionDTO;
import net.blueshell.api.model.converter.FormQuestion;
import org.mapstruct.*;

@Slf4j
@Mapper(componentModel = "spring", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public abstract class FormQuestionMapper extends BaseMapper<FormQuestion, FormQuestionDTO> {
    @Mapping(target = "prompt")
    @Mapping(target = "type")
    @Mapping(target = "options")
    @BeanMapping(ignoreByDefault = true)
    public abstract FormQuestion fromDTO(FormQuestionDTO dto, @MappingTarget FormQuestion formQuestion);

    @Mapping(target = "prompt")
    @Mapping(target = "type")
    @Mapping(target = "options")
    @BeanMapping(ignoreByDefault = true)
    public abstract FormQuestionDTO toDTO(FormQuestion formQuestion);
}

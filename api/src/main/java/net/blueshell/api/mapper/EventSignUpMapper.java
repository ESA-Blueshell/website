package net.blueshell.api.mapper;


import lombok.extern.slf4j.Slf4j;
import net.blueshell.api.base.BaseMapper;
import net.blueshell.api.dto.EventSignUpDTO;
import net.blueshell.api.mapper.user.SimpleUserMapper;
import net.blueshell.api.model.EventSignUp;
import net.blueshell.api.model.Guest;
import net.blueshell.api.model.User;
import org.mapstruct.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.ObjectUtils;

import java.time.LocalDateTime;

@Slf4j
@Mapper(componentModel = "spring", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE,
        uses = {GuestMapper.class})
public abstract class EventSignUpMapper extends BaseMapper<EventSignUp, EventSignUpDTO> {

    @Mapping(target = "id")
    @Mapping(target = "eventId")
    @Mapping(target = "guest")
    @Mapping(target = "userId")
    @Mapping(target = "formAnswers", source = "signUp.formAnswers")
    @BeanMapping(ignoreByDefault = true)
    public abstract EventSignUpDTO toDTO(EventSignUp signUp);

    @Mapping(target = "eventId")
    @Mapping(target = "guest")
    @Mapping(target = "userId")
    @Mapping(target = "signedUpAt", expression = "java(java.time.LocalDateTime.now())")
    @Mapping(target = "formAnswers", source = "dto.formAnswers")
    @BeanMapping(ignoreByDefault = true)
    public abstract EventSignUp fromDTO(EventSignUpDTO dto, @MappingTarget EventSignUp signUp);
}

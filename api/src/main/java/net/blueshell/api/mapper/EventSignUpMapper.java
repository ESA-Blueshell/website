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
        uses = {SimpleUserMapper.class, GuestMapper.class})
public abstract class EventSignUpMapper extends BaseMapper<EventSignUp, EventSignUpDTO> {

    @Autowired
    protected GuestMapper guestMapper;

    @Autowired
    protected SimpleUserMapper simpleUserMapper;

    @Mapping(target = "formAnswers", source = "signUp.formAnswers")
    @BeanMapping(ignoreByDefault = true)
    public abstract EventSignUpDTO toDTO(EventSignUp signUp);

    @AfterMapping
    protected void afterToDTO(EventSignUp signUp,
                              @MappingTarget EventSignUpDTO dto) {
        if (!ObjectUtils.isEmpty(signUp.getUser())) {
            dto.setUser(simpleUserMapper.toDTO(signUp.getUser()));
        } else if (!ObjectUtils.isEmpty(signUp.getGuest())) {
            dto.setGuest(guestMapper.toDTO(signUp.getGuest()));
        }
    }

    @Mapping(target = "formAnswers", source = "dto.formAnswers")
    @BeanMapping(ignoreByDefault = true)
    public abstract EventSignUp fromDTO(EventSignUpDTO dto, @MappingTarget EventSignUp signUp);

    @AfterMapping
    protected void afterFromDTO(EventSignUpDTO dto, @MappingTarget EventSignUp signUp) {
        User user = getPrincipal();
        if (signUp.getSignedUpAt() == null) {
            signUp.setSignedUpAt(LocalDateTime.now());
        }
        if (user != null) {
            signUp.setUserId(user.getId());
        } else {
            Guest guest = guestMapper.fromDTO(dto.getGuest());
            signUp.setGuest(guest);
        }
    }
}

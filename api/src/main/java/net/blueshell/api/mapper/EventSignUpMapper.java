package net.blueshell.api.mapper;


import net.blueshell.api.base.BaseMapper;
import net.blueshell.api.dto.EventSignUpDTO;
import net.blueshell.api.model.EventSignUp;
import net.blueshell.api.model.Guest;
import net.blueshell.api.model.User;
import org.mapstruct.AfterMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.ObjectUtils;

import java.time.LocalDateTime;

@Mapper(componentModel = "spring")
public abstract class EventSignUpMapper extends BaseMapper<EventSignUp, EventSignUpDTO> {

    @Autowired
    protected GuestMapper guestMapper;

    @Mapping(target = "fullName", ignore = true)
    @Mapping(target = "discord", ignore = true)
    @Mapping(target = "email", ignore = true)
    @Mapping(target = "formAnswers", source = "signUp.formAnswers")
    public abstract EventSignUpDTO toDTO(EventSignUp signUp);

    @AfterMapping
    protected void afterToDTO(EventSignUp signUp,
                              @MappingTarget EventSignUpDTO dto) {
        if (!ObjectUtils.isEmpty(signUp.getUser())) {
            dto.setFullName(signUp.getUser().getFullName());
            dto.setDiscord(signUp.getUser().getDiscord());
            dto.setEmail(signUp.getUser().getEmail());
        } else if (!ObjectUtils.isEmpty(signUp.getGuest())) {
            dto.setFullName(signUp.getGuest().getName());
            dto.setDiscord(signUp.getGuest().getDiscord());
            dto.setEmail(signUp.getGuest().getEmail());
        }
    }

    @Mapping(target = "event", ignore = true)
    @Mapping(target = "user", ignore = true)
    @Mapping(target = "guest", ignore = true)
    @Mapping(target = "deletedAt", ignore = true)
    @Mapping(target = "formAnswers", source = "dto.formAnswers")
    @Mapping(target = "signedUpAt", ignore = true)
    @Mapping(target = "userId", ignore = true)
    public abstract EventSignUp fromDTO(EventSignUpDTO dto);

    @AfterMapping
    protected void afterFromDTO(EventSignUpDTO dto, @MappingTarget EventSignUp signUp) {
        User user = getPrincipal();
        if (signUp.getSignedUpAt() == null) {
            signUp.setSignedUpAt(LocalDateTime.now());
        }
        if (user != null) {
            signUp.setUserId(user.getId());
        } else {
            Guest guest = guestMapper.fromDTO(dto);
            signUp.setGuest(guest);
        }
    }
}

package net.blueshell.api.mapper.event;


import lombok.extern.slf4j.Slf4j;
import net.blueshell.api.base.BaseMapper;
import net.blueshell.api.dto.event.EventSignUpDTO;
import net.blueshell.api.mapper.survey.AnswerMapper;
import net.blueshell.api.mapper.user.SimpleUserMapper;
import net.blueshell.api.model.event.EventSignUp;
import net.blueshell.api.service.GuestService;
import org.mapstruct.*;
import org.springframework.beans.factory.annotation.Autowired;

@Slf4j
@Mapper(componentModel = "spring",
        uses = {GuestMapper.class, AnswerMapper.class, SimpleUserMapper.class})
public abstract class EventSignUpMapper extends BaseMapper<EventSignUp, EventSignUpDTO> {
    @Mapping(target = "id")
    @Mapping(target = "eventId")
    @Mapping(target = "guest")
    @Mapping(target = "user")
    @Mapping(target = "answers")
    @Mapping(target = "version")
    @BeanMapping(ignoreByDefault = true)
    public abstract EventSignUpDTO toDTO(EventSignUp signUp);

    @Mapping(target = "eventId")
    @Mapping(target = "guest", ignore = true)
    @Mapping(target = "userId")
    @Mapping(target = "answers")
    @Mapping(target = "version")
    @BeanMapping(ignoreByDefault = true)
    public abstract EventSignUp fromDTO(EventSignUpDTO dto, @MappingTarget EventSignUp signUp);

    @Autowired
    private GuestService guests;

    @Autowired
    private GuestMapper guestMapper;

    @AfterMapping
    protected void afterFromDTO(EventSignUpDTO dto, @MappingTarget EventSignUp signUp) {
        if (dto.getGuest() != null && dto.getGuest().getAccessToken() != null) {
            var guest = guests.findByAccessToken(dto.getGuest().getAccessToken());
            guestMapper.fromDTO(dto.getGuest(), guest);
            signUp.setGuest(guest);
        } else if (dto.getGuest() != null) {
            signUp.setGuest(guestMapper.fromDTO(dto.getGuest()));
        }
    }
}

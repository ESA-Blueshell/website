package net.blueshell.api.mapper.event

import net.blueshell.api.mapper.base.BaseMapper
import net.blueshell.api.dto.event.EventSignUpDTO
import net.blueshell.api.mapper.survey.AnswerMapper
import net.blueshell.api.mapper.user.SimpleUserMapper
import net.blueshell.api.model.event.EventSignUp
import net.blueshell.api.service.GuestService
import org.mapstruct.*
import org.springframework.beans.factory.annotation.Autowired


@Mapper(componentModel = "spring", uses = [GuestMapper::class, AnswerMapper::class, SimpleUserMapper::class])
abstract class EventSignUpMapper : BaseMapper<EventSignUp, EventSignUpDTO>() {
    @Mapping(target = "eventId")
    @Mapping(target = "guest")
    @Mapping(target = "userId")
    @Mapping(target = "answers")
    @Mapping(target = "version")
    @BeanMapping(ignoreByDefault = true)
    abstract fun fromDTO(dto: EventSignUpDTO, @MappingTarget signUp: EventSignUp): EventSignUp

    @Mapping(target = "id")
    @Mapping(target = "eventId")
    @Mapping(target = "guest")
    @Mapping(target = "user")
    @Mapping(target = "userId")
    @Mapping(target = "answers")
    @Mapping(target = "version")
    @BeanMapping(ignoreByDefault = true)
    abstract override fun toDTO(signUp: EventSignUp): EventSignUpDTO
}

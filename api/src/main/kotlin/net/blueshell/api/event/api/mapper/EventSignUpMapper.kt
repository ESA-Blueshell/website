package net.blueshell.api.event.api.mapper

import net.blueshell.api.event.api.dto.EventSignUpDTO
import net.blueshell.api.shared.mapper.BaseMapper
import net.blueshell.api.survey.api.mapper.AnswerMapper
import net.blueshell.api.user.api.mapper.SimpleUserMapper
import net.blueshell.api.event.domain.model.EventSignUp
import org.mapstruct.BeanMapping
import org.mapstruct.Mapper
import org.mapstruct.Mapping
import org.mapstruct.MappingTarget


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

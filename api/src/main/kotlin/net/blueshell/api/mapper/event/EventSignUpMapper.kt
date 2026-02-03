package net.blueshell.api.mapper.event

import net.blueshell.api.base.BaseMapper
import net.blueshell.api.dto.event.EventSignUpDTO
import net.blueshell.api.mapper.survey.AnswerMapper
import net.blueshell.api.mapper.user.SimpleUserMapper
import net.blueshell.api.model.event.EventSignUp
import net.blueshell.api.service.GuestService
import org.mapstruct.*
import org.springframework.beans.factory.annotation.Autowired


@Mapper(componentModel = "spring", uses = [GuestMapper::class, AnswerMapper::class, SimpleUserMapper::class])
abstract class EventSignUpMapper : BaseMapper<EventSignUp, EventSignUpDTO>() {
    @Mapping(target = "id")
    @Mapping(target = "eventId")
    @Mapping(target = "guest")
    @Mapping(target = "user")
    @Mapping(target = "answers")
    @Mapping(target = "version")
    @BeanMapping(ignoreByDefault = true)
    abstract override fun toDTO(signUp: EventSignUp): EventSignUpDTO

    @Mapping(target = "eventId")
    @Mapping(target = "guest", ignore = true)
    @Mapping(target = "userId")
    @Mapping(target = "answers")
    @Mapping(target = "version")
    @BeanMapping(ignoreByDefault = true)
    abstract fun fromDTO(dto: EventSignUpDTO, @MappingTarget signUp: EventSignUp): EventSignUp

    @Autowired
    private lateinit var guests: GuestService

    @Autowired
    private lateinit var guestMapper: GuestMapper

    @AfterMapping
    protected fun afterFromDTO(dto: EventSignUpDTO, @MappingTarget signUp: EventSignUp) {
        if (dto.guest != null && dto.guest.accessToken != null) {
            val guest = guests.findByAccessToken(dto.guest.accessToken)
            guestMapper.fromDTO(dto.guest, guest)
            signUp.guest = guest
        } else if (dto.guest != null) {
            signUp.guest = guestMapper.fromDTO(dto.guest)
        }
    }
}

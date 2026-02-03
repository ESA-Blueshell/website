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
abstract class EventSignUpMapper : BaseMapper<EventSignUp?, EventSignUpDTO?>() {
    @Mapping(target = "id")
    @Mapping(target = "eventId")
    @Mapping(target = "guest")
    @Mapping(target = "user")
    @Mapping(target = "answers")
    @Mapping(target = "version")
    @BeanMapping(ignoreByDefault = true)
    abstract override fun toDTO(signUp: EventSignUp?): EventSignUpDTO?

    @Mapping(target = "eventId")
    @Mapping(target = "guest", ignore = true)
    @Mapping(target = "userId")
    @Mapping(target = "answers")
    @Mapping(target = "version")
    @BeanMapping(ignoreByDefault = true)
    abstract fun fromDTO(dto: EventSignUpDTO?, @MappingTarget signUp: EventSignUp?): EventSignUp?

    @Autowired
    private val guests: GuestService? = null

    @Autowired
    private val guestMapper: GuestMapper? = null

    @AfterMapping
    protected fun afterFromDTO(dto: EventSignUpDTO, @MappingTarget signUp: EventSignUp) {
        if (dto.getGuest() != null && dto.getGuest().getAccessToken() != null) {
            val guest = guests!!.findByAccessToken(dto.getGuest().getAccessToken())
            guestMapper!!.fromDTO(dto.getGuest(), guest)
            signUp.setGuest(guest)
        } else if (dto.getGuest() != null) {
            signUp.setGuest(guestMapper!!.fromDTO(dto.getGuest()))
        }
    }
}

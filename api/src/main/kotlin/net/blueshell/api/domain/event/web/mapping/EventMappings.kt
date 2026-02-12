package net.blueshell.api.domain.event.web.mapping

import net.blueshell.api.domain.blog.web.dto.SocialDTO
import net.blueshell.api.domain.committee.persistence.Committee
import net.blueshell.api.domain.event.persistence.Event
import net.blueshell.api.domain.event.persistence.EventBanner
import net.blueshell.api.domain.event.persistence.EventSignUp
import net.blueshell.api.domain.event.persistence.Guest
import net.blueshell.api.domain.event.web.dto.EventBannerDTO
import net.blueshell.api.domain.event.web.dto.EventBannerRequest
import net.blueshell.api.domain.event.web.dto.EventBannerResponse
import net.blueshell.api.domain.event.web.dto.EventResponse
import net.blueshell.api.domain.event.web.dto.EventDTO
import net.blueshell.api.domain.event.web.dto.EventSignUpDTO
import net.blueshell.api.domain.event.web.dto.EventSignUpResponse
import net.blueshell.api.domain.event.web.dto.CreateEventRequest
import net.blueshell.api.domain.event.web.dto.UpdateEventRequest
import net.blueshell.api.domain.event.web.dto.CreateEventSignUpRequest
import net.blueshell.api.domain.event.web.dto.UpdateEventSignUpRequest
import net.blueshell.api.domain.event.web.dto.CreateGuestRequest
import net.blueshell.api.domain.event.web.dto.GuestResponse
import net.blueshell.api.domain.event.web.dto.GuestDTO
import net.blueshell.api.domain.survey.web.mapping.asEntity
import net.blueshell.api.domain.survey.web.mapping.asDto
import net.blueshell.api.shared.enums.PlatformType
import net.blueshell.api.shared.enums.Role
import net.blueshell.api.shared.util.MappingUtil.randomCapitalString
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import tech.mappie.api.ObjectMappie

object EventToEventDTOMapper : ObjectMappie<Event, EventDTO>()

object EventBannerToEventBannerDTOMapper : ObjectMappie<EventBanner, EventBannerDTO>()

object GuestToGuestDTOMapper : ObjectMappie<Guest, GuestDTO>()

object EventSignUpToEventSignUpDTOMapper : ObjectMappie<EventSignUp, EventSignUpDTO>()

object EventDTOToSocialDTOMapper : ObjectMappie<EventDTO, SocialDTO>()

object EventToEventResponseMapper : ObjectMappie<Event, EventResponse>()

object EventBannerToEventBannerResponseMapper : ObjectMappie<EventBanner, EventBannerResponse>()

object GuestToGuestResponseMapper : ObjectMappie<Guest, GuestResponse>()

object EventSignUpToEventSignUpResponseMapper : ObjectMappie<EventSignUp, EventSignUpResponse>()

fun EventDTO.asEntity(event: Event = Event()): Event {
    event.committee = Committee::class.asRef(committeeId!!)
    event.title = title!!
    event.description = description
    event.location = location
    event.startTime = startTime!!
    event.endTime = endTime!!
    event.memberPrice = memberPrice
    event.publicPrice = publicPrice
    event.membersOnly = membersOnly!!
    event.signUp = signUp!!
    event.banner = banner?.asEntity()
    event.signUpForm = signUpForm?.asEntity()
    version?.let { event.version = it }
    event.approved = hasAuthority(Role.BOARD) && approved!!
    return event
}

fun CreateEventRequest.asEntity(event: Event = Event()): Event {
    event.committee = Committee::class.asRef(committeeId!!)
    event.title = title!!
    event.description = description
    event.location = location
    event.startTime = startTime!!
    event.endTime = endTime!!
    event.memberPrice = memberPrice
    event.publicPrice = publicPrice
    event.membersOnly = membersOnly!!
    event.signUp = signUp!!
    event.banner = banner?.asEntity()
    event.signUpForm = signUpForm?.asEntity()
    event.approved = hasAuthority(Role.BOARD) && approved!!
    return event
}

fun UpdateEventRequest.asEntity(event: Event = Event()): Event {
    event.committee = Committee::class.asRef(committeeId!!)
    event.title = title!!
    event.description = description
    event.location = location
    event.startTime = startTime!!
    event.endTime = endTime!!
    event.memberPrice = memberPrice
    event.publicPrice = publicPrice
    event.membersOnly = membersOnly!!
    event.signUp = signUp!!
    event.banner = banner?.asEntity()
    event.signUpForm = signUpForm?.asEntity()
    version?.let { event.version = it }
    event.approved = hasAuthority(Role.BOARD) && approved!!
    return event
}

fun EventBannerDTO.asEntity(banner: EventBanner = EventBanner()): EventBanner {
    banner.id.fileId = fileId
    version?.let { banner.version = it }
    return banner
}

fun EventBannerRequest.asEntity(banner: EventBanner = EventBanner()): EventBanner {
    banner.id.fileId = fileId
    version?.let { banner.version = it }
    return banner
}

fun GuestDTO.asEntity(guest: Guest = Guest()): Guest {
    guest.name = name!!
    guest.discord = requireNotNull(discord)
    guest.email = requireNotNull(email)
    guest.phoneNumber = phoneNumber
    version?.let { guest.version = it }

    if (guest.accessToken == null) {
        guest.accessToken = randomCapitalString(30)
    }

    return guest
}

fun CreateGuestRequest.asDto(guest: GuestDTO = GuestDTO()): GuestDTO {
    guest.name = name
    guest.discord = discord
    guest.email = email
    guest.phoneNumber = phoneNumber
    version?.let { guest.version = it }
    return guest
}

fun EventSignUpDTO.asEntity(signUp: EventSignUp = EventSignUp()): EventSignUp {
    signUp.event = Event::class.asRef(eventId!!)
    userId?.let { signUp.userId = it }
    signUp.guest = guest?.asEntity()

    if (answers != null) {
        val mappedAnswers = answers!!.map { it.asEntity() }
        val answersSet = signUp.answers as MutableSet
        answersSet.clear()
        answersSet.addAll(mappedAnswers)
    }

    version?.let { signUp.version = it }
    return signUp
}

fun CreateEventSignUpRequest.asDto(eventId: Long, dto: EventSignUpDTO = EventSignUpDTO()): EventSignUpDTO {
    dto.eventId = eventId
    dto.answers = answers?.map { it.asDto() }?.toMutableList()
    dto.guest = guest?.asDto()
    dto.userId = userId
    return dto
}

fun UpdateEventSignUpRequest.asDto(eventId: Long, dto: EventSignUpDTO = EventSignUpDTO()): EventSignUpDTO {
    dto.eventId = eventId
    dto.answers = answers?.map { it.asDto() }?.toMutableList()
    dto.guest = guest?.asDto()
    dto.userId = userId
    version?.let { dto.version = it }
    return dto
}

fun EventDTO.asSocialDto(): SocialDTO {
    val socialDTO = EventDTOToSocialDTOMapper.map(this)
    socialDTO.text = description
    socialDTO.platforms = arrayOf(PlatformType.FACEBOOK, PlatformType.TWITTER, PlatformType.INSTAGRAM)
    return socialDTO
}

private fun hasAuthority(role: Role): Boolean {
    val authentication = SecurityContextHolder.getContext().authentication
    return authentication != null && authentication.authorities.any { a: GrantedAuthority? ->
        a?.authority == role.toString()
    }
}

fun Event.asDto(): EventDTO = EventToEventDTOMapper.map(this)

fun EventBanner.asDto(): EventBannerDTO = EventBannerToEventBannerDTOMapper.map(this)

fun Guest.asDto(): GuestDTO = GuestToGuestDTOMapper.map(this)

fun EventSignUp.asDto(): EventSignUpDTO = EventSignUpToEventSignUpDTOMapper.map(this)

fun Event.asResponse(): EventResponse = EventToEventResponseMapper.map(this)

fun EventBanner.asResponse(): EventBannerResponse = EventBannerToEventBannerResponseMapper.map(this)

fun Guest.asResponse(): GuestResponse = GuestToGuestResponseMapper.map(this)

fun EventSignUp.asResponse(): EventSignUpResponse = EventSignUpToEventSignUpResponseMapper.map(this)

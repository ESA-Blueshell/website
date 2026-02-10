package net.blueshell.api.event.web.mapping

import net.blueshell.api.blog.web.dto.SocialDTO
import net.blueshell.api.event.persistence.Event
import net.blueshell.api.event.persistence.EventBanner
import net.blueshell.api.event.persistence.EventSignUp
import net.blueshell.api.event.persistence.Guest
import net.blueshell.api.event.web.dto.EventBannerDTO
import net.blueshell.api.event.web.dto.EventDTO
import net.blueshell.api.event.web.dto.EventSignUpDTO
import net.blueshell.api.event.web.dto.GuestDTO
import net.blueshell.api.file.web.mapping.asEntity
import net.blueshell.api.shared.enums.PlatformType
import net.blueshell.api.shared.enums.Role
import net.blueshell.api.shared.util.MappingUtil.randomCapitalString
import net.blueshell.api.survey.web.mapping.asEntity
import net.blueshell.api.user.persistence.User
import net.blueshell.api.user.web.dto.SimpleUserDTO
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import tech.mappie.api.ObjectMappie

object EventToEventDTOMapper : ObjectMappie<Event, EventDTO>()

object EventBannerToEventBannerDTOMapper : ObjectMappie<EventBanner, EventBannerDTO>()

object GuestToGuestDTOMapper : ObjectMappie<Guest, GuestDTO>()

object EventSignUpToEventSignUpDTOMapper : ObjectMappie<EventSignUp, EventSignUpDTO>()

object EventDTOToSocialDTOMapper : ObjectMappie<EventDTO, SocialDTO>()

fun EventDTO.asEntity(existing: Event = Event()): Event {
    existing.committeeId = committeeId!!
    existing.title = title!!
    existing.description = description
    existing.location = location
    existing.startTime = startTime!!
    existing.endTime = endTime!!
    existing.memberPrice = memberPrice
    existing.publicPrice = publicPrice
    existing.membersOnly = membersOnly
    existing.signUp = signUp
    existing.banner = banner?.asEntity()
    existing.signUpForm = signUpForm?.asEntity()
    existing.version = version
    existing.approved = hasAuthority(Role.BOARD) && approved
    return existing
}

fun EventBannerDTO.asEntity(banner: EventBanner = EventBanner()): EventBanner {
    banner.file = file.asEntity()
    banner.version = version
    return banner
}

fun GuestDTO.asEntity(guest: Guest = Guest()): Guest {
    guest.name = name
    guest.discord = requireNotNull(discord)
    guest.email = requireNotNull(email)
    guest.phoneNumber = phoneNumber
    guest.version = version

    if (guest.accessToken == null) {
        guest.accessToken = randomCapitalString(30)
    }

    return guest
}

fun EventSignUpDTO.asEntity(signUp: EventSignUp = EventSignUp()): EventSignUp {
    signUp.eventId = eventId
    userId?.let { signUp.userId = it }
    signUp.guest = guest?.asEntity()

    val mappedAnswers = answers.map { it.asEntity() }
    val answers = signUp.answers as MutableSet
    answers.clear()
    answers.addAll(mappedAnswers)

    signUp.version = version
    return signUp
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

private fun SimpleUserDTO.asUserEntityForSignUp(): User {
    val user = User()
    user.username = requireNotNull(username)
    user.password = requireNotNull(password)
    user.firstName = requireNotNull(firstName)
    user.lastName = requireNotNull(lastName)
    user.email = requireNotNull(email)
    initials?.let { user.initials = it }
    prefix?.let { user.prefix = it }
    discord?.let { user.discord = it }
    phoneNumber?.let { user.phoneNumber = it }
    user.newsletter = newsletter
    addressId?.let { user.addressId = it }
    user.version = version
    return user
}

fun Event.asDto(): EventDTO = EventToEventDTOMapper.map(this)

fun EventBanner.asDto(): EventBannerDTO = EventBannerToEventBannerDTOMapper.map(this)

fun Guest.asDto(): GuestDTO = GuestToGuestDTOMapper.map(this)

fun EventSignUp.asDto(): EventSignUpDTO = EventSignUpToEventSignUpDTOMapper.map(this)

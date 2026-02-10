package net.blueshell.api.event.web.mapping

import net.blueshell.api.shared.enums.PlatformType
import net.blueshell.api.shared.enums.Role
import net.blueshell.api.shared.util.MappingUtil.randomCapitalString
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
import net.blueshell.api.survey.web.mapping.asEntity
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.beans.BeanUtils
import tech.mappie.api.ObjectMappie

object EventToEventDTOMapper : ObjectMappie<Event, EventDTO>()

object EventDTOToEventMapper : ObjectMappie<EventDTO, Event>()

object EventBannerToEventBannerDTOMapper : ObjectMappie<EventBanner, EventBannerDTO>()

object EventBannerDTOToEventBannerMapper : ObjectMappie<EventBannerDTO, EventBanner>()

object GuestToGuestDTOMapper : ObjectMappie<Guest, GuestDTO>()

object GuestDTOToGuestMapper : ObjectMappie<GuestDTO, Guest>() {
    override fun map(from: GuestDTO) = mapping {
        Guest::discord fromValue from.discord!!
        Guest::email fromValue from.email!!
    }
}

object EventSignUpToEventSignUpDTOMapper : ObjectMappie<EventSignUp, EventSignUpDTO>()

object EventSignUpDTOToEventSignUpMapper : ObjectMappie<EventSignUpDTO, EventSignUp>()

object EventDTOToSocialDTOMapper : ObjectMappie<EventDTO, SocialDTO>()

fun EventDTO.asEntity(existing: Event = Event()): Event {
    val mapped = EventDTOToEventMapper.map(this)
    BeanUtils.copyProperties(
        mapped,
        existing,
        "approved",
        "banner",
        "googleId",
        "pictures",
        "signUpCount",
        "signUpForm"
    )
    existing.banner = banner?.asEntity()
    existing.signUpForm = signUpForm?.asEntity()
    signUpCount?.let { existing.signUpCount = it }
    existing.version = mapped.version
    existing.approved = if (hasAuthority(Role.BOARD)) approved else false
    return existing
}

fun EventBannerDTO.asEntity(banner: EventBanner = EventBanner()): EventBanner {
    val mapped = EventBannerDTOToEventBannerMapper.map(this)
    banner.file = requireNotNull(file).asEntity()
    banner.version = mapped.version
    return banner
}

fun GuestDTO.asEntity(guest: Guest = Guest()): Guest {
    val mapped = GuestDTOToGuestMapper.map(this)
    guest.name = mapped.name
    guest.discord = mapped.discord
    guest.email = mapped.email
    guest.phoneNumber = mapped.phoneNumber
    guest.version = mapped.version

    if (guest.accessToken == null) {
        guest.accessToken = randomCapitalString(30)
    }

    return guest
}

fun EventSignUpDTO.asEntity(signUp: EventSignUp = EventSignUp()): EventSignUp {
    val mapped = EventSignUpDTOToEventSignUpMapper.map(this)
    mapped.eventId?.let { signUp.eventId = it }
    mapped.userId?.let { signUp.userId = it }
    mapped.user?.let { signUp.user = it }
    signUp.guest = guest?.asEntity()

    val mappedAnswers = answers.map { it.asEntity() }
    val answers = signUp.answers as MutableSet
    answers.clear()
    answers.addAll(mappedAnswers)

    version?.let { signUp.version = it }
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

fun Event.asDto(): EventDTO = EventToEventDTOMapper.map(this)

fun EventBanner.asDto(): EventBannerDTO = EventBannerToEventBannerDTOMapper.map(this)

fun Guest.asDto(): GuestDTO = GuestToGuestDTOMapper.map(this)

fun EventSignUp.asDto(): EventSignUpDTO = EventSignUpToEventSignUpDTOMapper.map(this)

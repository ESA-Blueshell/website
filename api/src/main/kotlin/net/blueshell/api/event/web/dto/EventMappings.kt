package net.blueshell.api.event.web.dto

import io.mcarle.konvert.api.Konvert
import io.mcarle.konvert.api.Konverter
import io.mcarle.konvert.api.Mapping
import net.blueshell.api.shared.enums.PlatformType
import net.blueshell.api.shared.enums.Role
import net.blueshell.api.shared.util.MappingUtil.randomCapitalString
import net.blueshell.api.blog.web.dto.SocialDTO
import net.blueshell.api.event.persistence.Event
import net.blueshell.api.event.persistence.EventBanner
import net.blueshell.api.event.persistence.EventSignUp
import net.blueshell.api.event.persistence.Guest
import net.blueshell.api.file.web.dto.asEntity
import net.blueshell.api.survey.web.dto.asEntity
import net.blueshell.api.user.persistence.asSimpleDto
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder

@Konverter
interface EventKonverter {
    @Konvert(
        mappings = [
            Mapping(target = "banner", ignore = true),
            Mapping(target = "signUpForm", ignore = true),
        ]
    )
    fun toDTO(event: Event): EventDTO

    @Konvert(
        mappings = [
            Mapping(target = "banner", ignore = true),
            Mapping(target = "signUpForm", ignore = true),
        ]
    )
    fun fromDTO(dto: EventDTO): Event
}

@Konverter
interface EventBannerKonverter {
    @Konvert(mappings = [Mapping(target = "file", ignore = true)])
    fun toDTO(banner: EventBanner): EventBannerDTO

    @Konvert(mappings = [Mapping(target = "file", ignore = true)])
    fun fromDTO(dto: EventBannerDTO): EventBanner
}

@Konverter
interface GuestKonverter {
    fun toDTO(guest: Guest): GuestDTO

    fun fromDTO(dto: GuestDTO): Guest
}

@Konverter
interface EventSignUpKonverter {
    @Konvert(
        mappings = [
            Mapping(target = "answers", ignore = true),
            Mapping(target = "guest", ignore = true),
            Mapping(target = "user", ignore = true),
        ]
    )
    fun toDTO(signUp: EventSignUp): EventSignUpDTO

    @Konvert(
        mappings = [
            Mapping(target = "answers", ignore = true),
            Mapping(target = "guest", ignore = true),
            Mapping(target = "user", ignore = true),
        ]
    )
    fun fromDTO(dto: EventSignUpDTO): EventSignUp
}

@Konverter
interface EventSocialKonverter {
    fun toSocialDto(dto: EventDTO): SocialDTO
}

private val eventKonverter = Konverter.get<EventKonverter>()
private val eventBannerKonverter = Konverter.get<EventBannerKonverter>()
private val guestKonverter = Konverter.get<GuestKonverter>()
private val eventSignUpKonverter = Konverter.get<EventSignUpKonverter>()
private val eventSocialKonverter = Konverter.get<EventSocialKonverter>()

fun EventDTO.asEntity(event: Event = Event()): Event {
    val mapped = eventKonverter.fromDTO(this)
    event.committeeId = mapped.committeeId
    event.title = mapped.title
    event.description = mapped.description
    event.location = mapped.location
    event.startTime = requireNotNull(startTime)
    event.endTime = requireNotNull(endTime)
    event.memberPrice = mapped.memberPrice
    event.publicPrice = mapped.publicPrice
    event.membersOnly = mapped.membersOnly
    event.banner = banner?.asEntity()
    event.signUpForm = signUpForm?.asEntity()
    event.signUp = mapped.signUp
    signUpCount?.let { event.signUpCount = it }
    version?.let { event.version = it }
    event.approved = if (hasAuthority(Role.BOARD)) approved else false
    return event
}

fun EventBannerDTO.asEntity(banner: EventBanner = EventBanner()): EventBanner {
    banner.file = requireNotNull(file).asEntity()
    version?.let { banner.version = it }
    return banner
}

fun GuestDTO.asEntity(guest: Guest = Guest()): Guest {
    val mapped = guestKonverter.fromDTO(this)
    mapped.name?.let { guest.name = it }
    mapped.discord?.let { guest.discord = it }
    mapped.email?.let { guest.email = it }
    mapped.phoneNumber?.let { guest.phoneNumber = it }
    version?.let { guest.version = it }

    if (guest.accessToken == null) {
        guest.accessToken = randomCapitalString(30)
    }

    return guest
}

fun EventSignUpDTO.asEntity(signUp: EventSignUp = EventSignUp()): EventSignUp {
    val mapped = eventSignUpKonverter.fromDTO(this)
    mapped.eventId?.let { signUp.eventId = it }
    mapped.userId?.let { signUp.userId = it }
    signUp.guest = guest?.asEntity()

    val mappedAnswers = answers.map { it.asEntity() }
    val answers = signUp.answers as MutableSet
    answers.clear()
    answers.addAll(mappedAnswers)

    version?.let { signUp.version = it }
    return signUp
}

fun EventDTO.asSocialDto(): SocialDTO {
    val socialDTO = eventSocialKonverter.toSocialDto(this)
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

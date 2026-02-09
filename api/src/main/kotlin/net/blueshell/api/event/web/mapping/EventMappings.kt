package net.blueshell.api.event.web.mapping

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
import net.blueshell.api.event.web.dto.EventBannerDTO
import net.blueshell.api.event.web.dto.EventDTO
import net.blueshell.api.event.web.dto.EventSignUpDTO
import net.blueshell.api.event.web.dto.GuestDTO
import net.blueshell.api.file.web.mapping.asEntity
import net.blueshell.api.survey.web.mapping.asEntity
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.beans.BeanUtils

@Konverter
interface EventKonverter {
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
    fun toDTO(signUp: EventSignUp): EventSignUpDTO

    @Konvert(
        mappings = [
            Mapping(target = "answers", ignore = true),
            Mapping(target = "guest", ignore = true),
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

fun EventDTO.asEntity(existing: Event = Event()): Event {
    requireNotNull(startTime) { "startTime is required" }
    requireNotNull(endTime) { "endTime is required" }
    val mapped = eventKonverter.fromDTO(this)
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
    val mapped = eventBannerKonverter.fromDTO(this)
    banner.file = requireNotNull(file).asEntity()
    banner.version = mapped.version
    return banner
}

fun GuestDTO.asEntity(guest: Guest = Guest()): Guest {
    val mapped = guestKonverter.fromDTO(this)
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
    val mapped = eventSignUpKonverter.fromDTO(this)
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

fun Event.asDto(): EventDTO = eventKonverter.toDTO(this)

fun EventBanner.asDto(): EventBannerDTO = eventBannerKonverter.toDTO(this)

fun Guest.asDto(): GuestDTO = guestKonverter.toDTO(this)

fun EventSignUp.asDto(): EventSignUpDTO = eventSignUpKonverter.toDTO(this)

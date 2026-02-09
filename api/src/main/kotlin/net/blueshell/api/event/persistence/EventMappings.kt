package net.blueshell.api.event.persistence

import io.mcarle.konvert.api.Konverter
import net.blueshell.api.event.web.dto.EventBannerDTO
import net.blueshell.api.event.web.dto.EventBannerKonverter
import net.blueshell.api.event.web.dto.EventDTO
import net.blueshell.api.event.web.dto.EventKonverter
import net.blueshell.api.event.web.dto.EventSignUpDTO
import net.blueshell.api.event.web.dto.EventSignUpKonverter
import net.blueshell.api.event.web.dto.GuestDTO
import net.blueshell.api.event.web.dto.GuestKonverter
import net.blueshell.api.survey.web.dto.asDto
import net.blueshell.api.user.persistence.asSimpleDto

private val eventKonverter = Konverter.get<EventKonverter>()
private val eventBannerKonverter = Konverter.get<EventBannerKonverter>()
private val guestKonverter = Konverter.get<GuestKonverter>()
private val eventSignUpKonverter = Konverter.get<EventSignUpKonverter>()

fun Event.asDto(): EventDTO {
    val dto = eventKonverter.toDTO(this)
    dto.banner = banner?.asDto()
    dto.signUpForm = signUpForm?.asDto()
    return dto
}

fun EventBanner.asDto(): EventBannerDTO {
    val dto = eventBannerKonverter.toDTO(this)
    dto.file = file.asDto()
    return dto
}

fun Guest.asDto(): GuestDTO = guestKonverter.toDTO(this)

fun EventSignUp.asDto(): EventSignUpDTO {
    val dto = eventSignUpKonverter.toDTO(this)
    dto.answers = answers.map { it.asDto() }.toMutableList()
    dto.guest = guest?.asDto()
    dto.user = user?.asSimpleDto()
    return dto
}

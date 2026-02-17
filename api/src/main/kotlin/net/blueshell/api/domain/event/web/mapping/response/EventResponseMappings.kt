package net.blueshell.api.domain.event.web.mapping.response

import net.blueshell.api.domain.event.persistence.Event
import net.blueshell.api.domain.event.persistence.EventBanner
import net.blueshell.api.domain.event.persistence.EventSignUp
import net.blueshell.api.domain.event.persistence.Guest
import net.blueshell.api.domain.event.web.dto.response.EventBannerResponse
import net.blueshell.api.domain.event.web.dto.response.EventResponse
import net.blueshell.api.domain.event.web.dto.response.EventSignUpResponse
import net.blueshell.api.domain.event.web.dto.response.GuestResponse
import net.blueshell.api.domain.survey.web.mapping.response.asResponse
import net.blueshell.api.domain.user.web.mapping.response.asSummaryResponse
import tech.mappie.api.ObjectMappie

object EventToEventResponseMapper : ObjectMappie<Event, EventResponse>() {
    override fun map(from: Event) = mapping {
        EventResponse::id fromValue from.id!!
        EventResponse::banner fromValue from.banner?.asResponse()
        EventResponse::signUpForm fromValue from.signUpForm?.asResponse()
    }
}

object EventBannerToEventBannerResponseMapper : ObjectMappie<EventBanner, EventBannerResponse>()

object GuestToGuestResponseMapper : ObjectMappie<Guest, GuestResponse>() {
    override fun map(from: Guest) = mapping {
        GuestResponse::id fromValue from.id!!
        GuestResponse::accessToken fromValue from.accessToken!!
    }
}

object EventSignUpToEventSignUpResponseMapper : ObjectMappie<EventSignUp, EventSignUpResponse>() {
    override fun map(from: EventSignUp) = mapping {
        EventSignUpResponse::id fromValue from.id!!
        EventSignUpResponse::answers fromValue from.answers.map { it.asResponse() }.toMutableList()
        EventSignUpResponse::guest fromValue from.guest?.asResponse()
        EventSignUpResponse::user fromValue from.user?.asSummaryResponse()
    }
}

fun Event.asResponse(): EventResponse = EventToEventResponseMapper.map(this)

fun EventBanner.asResponse(): EventBannerResponse = EventBannerToEventBannerResponseMapper.map(this)

fun Guest.asResponse(): GuestResponse = GuestToGuestResponseMapper.map(this)

fun EventSignUp.asResponse(): EventSignUpResponse = EventSignUpToEventSignUpResponseMapper.map(this)

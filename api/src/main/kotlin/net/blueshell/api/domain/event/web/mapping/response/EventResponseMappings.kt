package net.blueshell.api.domain.event.web.mapping.response

import net.blueshell.api.domain.event.persistence.Event
import net.blueshell.api.domain.event.persistence.EventBanner
import net.blueshell.api.domain.event.persistence.EventSignUp
import net.blueshell.api.domain.event.persistence.Guest
import net.blueshell.api.domain.event.web.dto.response.EventBannerResponse
import net.blueshell.api.domain.event.web.dto.response.EventResponse
import net.blueshell.api.domain.event.web.dto.response.EventSignUpResponse
import net.blueshell.api.domain.event.web.dto.response.GuestResponse
import tech.mappie.api.ObjectMappie

object EventToEventResponseMapper : ObjectMappie<Event, EventResponse>()

object EventBannerToEventBannerResponseMapper : ObjectMappie<EventBanner, EventBannerResponse>()

object GuestToGuestResponseMapper : ObjectMappie<Guest, GuestResponse>()

object EventSignUpToEventSignUpResponseMapper : ObjectMappie<EventSignUp, EventSignUpResponse>()

fun Event.asResponse(): EventResponse = EventToEventResponseMapper.map(this)

fun EventBanner.asResponse(): EventBannerResponse = EventBannerToEventBannerResponseMapper.map(this)

fun Guest.asResponse(): GuestResponse = GuestToGuestResponseMapper.map(this)

fun EventSignUp.asResponse(): EventSignUpResponse = EventSignUpToEventSignUpResponseMapper.map(this)

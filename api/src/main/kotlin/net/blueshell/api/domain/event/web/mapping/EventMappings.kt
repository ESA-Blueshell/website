package net.blueshell.api.domain.event.web.mapping

import net.blueshell.api.domain.blog.web.dto.SocialDTO
import net.blueshell.api.domain.event.persistence.Event
import net.blueshell.api.domain.event.persistence.EventBanner
import net.blueshell.api.domain.event.persistence.EventSignUp
import net.blueshell.api.domain.event.persistence.Guest
import net.blueshell.api.domain.event.web.dto.EventBannerDTO
import net.blueshell.api.domain.event.web.dto.response.EventBannerResponse
import net.blueshell.api.domain.event.web.dto.EventDTO
import net.blueshell.api.domain.event.web.dto.response.EventResponse
import net.blueshell.api.domain.event.web.dto.EventSignUpDTO
import net.blueshell.api.domain.event.web.dto.response.EventSignUpResponse
import net.blueshell.api.domain.event.web.dto.GuestDTO
import net.blueshell.api.domain.event.web.dto.response.GuestResponse
import net.blueshell.api.shared.enums.PlatformType
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

fun EventDTO.asSocialDto(): SocialDTO {
    val socialDTO = EventDTOToSocialDTOMapper.map(this)
    socialDTO.text = description
    socialDTO.platforms = arrayOf(PlatformType.FACEBOOK, PlatformType.TWITTER, PlatformType.INSTAGRAM)
    return socialDTO
}

fun Event.asDto(): EventDTO = EventToEventDTOMapper.map(this)

fun EventBanner.asDto(): EventBannerDTO = EventBannerToEventBannerDTOMapper.map(this)

fun Guest.asDto(): GuestDTO = GuestToGuestDTOMapper.map(this)

fun EventSignUp.asDto(): EventSignUpDTO = EventSignUpToEventSignUpDTOMapper.map(this)

fun Event.asResponse(): EventResponse = EventToEventResponseMapper.map(this)

fun EventBanner.asResponse(): EventBannerResponse = EventBannerToEventBannerResponseMapper.map(this)

fun Guest.asResponse(): GuestResponse = GuestToGuestResponseMapper.map(this)

fun EventSignUp.asResponse(): EventSignUpResponse = EventSignUpToEventSignUpResponseMapper.map(this)

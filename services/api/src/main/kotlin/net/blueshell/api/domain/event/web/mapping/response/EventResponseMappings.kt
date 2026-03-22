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

fun Event.asResponse(): EventResponse =
    EventResponse(
        id = this.id!!,
        committeeId = this.committeeId,
        title = this.title,
        description = this.description,
        location = this.location,
        startTime = this.startTime,
        endTime = this.endTime,
        memberPrice = this.memberPrice,
        publicPrice = this.publicPrice,
        approved = this.approved,
        membersOnly = this.membersOnly,
        signUp = this.signUp,
        signUpDeadline = this.signUpDeadline,
        signUpLimit = this.signUpLimit,
        banner = this.banner?.asResponse(),
        signUpCount = this.signUpCount,
        signUpForm = this.signUpForm?.asResponse(),
        version = this.version,
        createdAt = this.createdAt,
        updatedAt = this.updatedAt,
    )

fun EventBanner.asResponse(): EventBannerResponse =
    EventBannerResponse(
        eventId = this.eventId,
        fileId = this.fileId,
        version = this.version,
        createdAt = this.createdAt,
        updatedAt = this.updatedAt,
    )

fun Guest.asResponse(): GuestResponse =
    GuestResponse(
        id = this.id!!,
        name = this.name,
        email = this.email,
        discord = this.discord,
        phoneNumber = this.phoneNumber,
        version = this.version,
        createdAt = this.createdAt,
        updatedAt = this.updatedAt,
    )

fun EventSignUp.asResponse(): EventSignUpResponse =
    EventSignUpResponse(
        id = this.id!!,
        eventId = this.eventId,
        answers = this.answers.map { it.asResponse() }.toMutableList(),
        guest = this.guest?.asResponse(),
        user = this.user?.asSummaryResponse(),
        version = this.version,
        createdAt = this.createdAt,
        updatedAt = this.updatedAt,
    )

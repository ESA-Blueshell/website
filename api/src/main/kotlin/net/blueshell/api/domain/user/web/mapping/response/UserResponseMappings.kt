package net.blueshell.api.domain.user.web.mapping.response

import net.blueshell.api.domain.user.persistence.User
import net.blueshell.api.domain.user.web.dto.response.UserDetailResponse
import net.blueshell.api.domain.user.web.dto.response.UserSummaryResponse

fun User.asDetailResponse(): UserDetailResponse =
    UserDetailResponse(
        id = this.id!!,
        username = this.username,
        initials = this.initials,
        firstName = this.firstName,
        prefix = this.prefix,
        lastName = this.lastName,
        newsletter = this.newsletter,
        email = this.email,
        discord = this.discord,
        phoneNumber = this.phoneNumber,
        version = this.version,
        roles = this.inheritedRoles,
        enabled = this.enabled,
        fullName = this.fullName,
        createdAt = this.createdAt,
        updatedAt = this.updatedAt
    )

fun User.asSummaryResponse(): UserSummaryResponse =
    UserSummaryResponse(
        id = this.id!!,
        version = this.version,
        createdAt = this.createdAt,
        updatedAt = this.updatedAt,
        email = this.email,
        discord = this.discord,
        phoneNumber = this.phoneNumber,
        fullName = this.fullName,
    )

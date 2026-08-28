package net.blueshell.api.user.web

import net.blueshell.api.user.persistence.DeletedUser
import net.blueshell.api.user.persistence.User

fun User.asDetailResponse(): UserDetailResponse =
    UserDetailResponse(
        id = this.id!!,
        username = this.username,
        initials = this.initials,
        firstName = this.firstName,
        prefix = this.prefix,
        lastName = this.lastName,
        newsletter = this.newsletter,
        photoConsent = this.photoConsent,
        email = this.email,
        discord = this.discord,
        phoneNumber = this.phoneNumber,
        addressId = this.addressId,
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

fun DeletedUser.asDetailResponse(): UserDetailResponse =
    UserDetailResponse(
        id = this.userId,
        roles = emptySet(),
        enabled = this.enabled,
        username = this.username,
        initials = this.initials,
        firstName = this.firstName,
        prefix = this.prefix,
        lastName = this.lastName,
        fullName = this.fullName,
        newsletter = this.newsletter,
        photoConsent = this.photoConsent,
        email = this.email,
        discord = this.discord,
        phoneNumber = this.phoneNumber,
        addressId = null,
        restoreUntilAt = this.restoreUntilAt,
        createdAt = this.createdAt,
        updatedAt = this.updatedAt,
        version = this.version
    )

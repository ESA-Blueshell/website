package net.blueshell.api.domain.user.web.mapping.response

import net.blueshell.api.domain.user.persistence.MemberProfile
import net.blueshell.api.domain.user.web.dto.response.Gender
import net.blueshell.api.domain.user.web.dto.response.MemberProfileResponse

fun MemberProfile.asResponse(): MemberProfileResponse =
    MemberProfileResponse(
        id = this.id!!,
        userId = this.userId!!,
        dateOfBirth = this.dateOfBirth?.toLocalDate(),
        studentNumber = this.studentNumber,
        gender = this.gender?.let { Gender.valueOf(it.uppercase()) },
        nationality = this.nationality,
        bhv = this.bhv,
        ehbo = this.ehbo,
        createdAt = this.createdAt,
        updatedAt = this.updatedAt,
        version = this.version
    )

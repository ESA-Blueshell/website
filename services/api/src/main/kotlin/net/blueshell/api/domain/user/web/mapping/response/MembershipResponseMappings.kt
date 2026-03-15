package net.blueshell.api.domain.user.web.mapping.response

import net.blueshell.api.domain.user.persistence.Membership
import net.blueshell.api.domain.user.web.dto.response.MembershipResponse

fun Membership.asResponse(): MembershipResponse =
    MembershipResponse(
        userId = this.userId,
        memberType = this.memberType,
        startDate = this.startDate,
        endDate = this.endDate,
        incasso = this.incasso,
        version = this.version,
        id = this.id!!,
        createdAt = this.createdAt,
        updatedAt = this.updatedAt,
    )

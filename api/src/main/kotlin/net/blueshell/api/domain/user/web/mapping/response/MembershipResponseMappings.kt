package net.blueshell.api.domain.user.web.mapping.response

import net.blueshell.api.domain.user.persistence.Membership
import net.blueshell.api.domain.user.web.dto.response.MembershipResponse
import tech.mappie.api.ObjectMappie

object MembershipToMembershipResponseMapper : ObjectMappie<Membership, MembershipResponse>() {
    override fun map(from: Membership) = mapping {
        MembershipResponse::id fromValue from.id!!
    }
}

fun Membership.asResponse(): MembershipResponse = MembershipToMembershipResponseMapper.map(this)

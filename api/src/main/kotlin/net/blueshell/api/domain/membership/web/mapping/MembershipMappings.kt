package net.blueshell.api.domain.membership.web.mapping

import net.blueshell.api.domain.membership.persistence.Membership
import net.blueshell.api.domain.membership.web.dto.response.MembershipResponse
import tech.mappie.api.ObjectMappie

object MembershipToMembershipResponseMapper : ObjectMappie<Membership, MembershipResponse>()

fun Membership.asResponse(): MembershipResponse = MembershipToMembershipResponseMapper.map(this)

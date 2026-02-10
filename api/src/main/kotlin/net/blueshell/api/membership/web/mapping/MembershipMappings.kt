package net.blueshell.api.membership.web.mapping

import net.blueshell.api.membership.persistence.Membership
import net.blueshell.api.membership.web.dto.MembershipDTO
import net.blueshell.api.shared.enums.Role
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import tech.mappie.api.ObjectMappie

object MembershipToMembershipDTOMapper : ObjectMappie<Membership, MembershipDTO>()

fun MembershipDTO.asEntity(membership: Membership = Membership()): Membership {
    membership.userId = userId!!
    membership.version = version!!

    if (hasAuthority(Role.BOARD)) {
        membership.startDate = startDate!!
        membership.endDate = endDate
        membership.memberType = memberType!!
        membership.incasso = incasso!!
    }

    return membership
}

private fun hasAuthority(role: Role): Boolean {
    val authentication = SecurityContextHolder.getContext().authentication
    return authentication != null && authentication.authorities.any { a: GrantedAuthority? ->
        a?.authority == role.toString()
    }
}

fun Membership.asDto(): MembershipDTO = MembershipToMembershipDTOMapper.map(this)

package net.blueshell.api.membership.web.mapping

import net.blueshell.api.shared.enums.Role
import net.blueshell.api.membership.persistence.Membership
import net.blueshell.api.membership.web.dto.MembershipDTO
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import tech.mappie.api.ObjectMappie

object MembershipToMembershipDTOMapper : ObjectMappie<Membership, MembershipDTO>()

object MembershipDTOToMembershipMapper : ObjectMappie<MembershipDTO, Membership>()

fun MembershipDTO.asEntity(membership: Membership = Membership()): Membership {
    val mapped = MembershipDTOToMembershipMapper.map(this)
    mapped.userId?.let { membership.userId = it }
    version?.let { membership.version = it }

    if (hasAuthority(Role.BOARD)) {
        mapped.startDate?.let { membership.startDate = it }
        membership.endDate = mapped.endDate
        mapped.memberType?.let { membership.memberType = it }
        membership.incasso = mapped.incasso
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

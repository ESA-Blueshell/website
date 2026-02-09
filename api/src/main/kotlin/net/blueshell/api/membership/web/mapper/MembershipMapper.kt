package net.blueshell.api.membership.web.mapper

import net.blueshell.api.shared.enums.Role
import net.blueshell.api.membership.web.dto.MembershipDTO
import net.blueshell.api.shared.mapper.BaseMapper
import net.blueshell.api.membership.persistence.Membership
import org.springframework.stereotype.Component

@Component
class MembershipMapper : BaseMapper<Membership, MembershipDTO>() {
    override fun fromDTO(dto: MembershipDTO): Membership = fromDTO(dto, Membership())

    fun fromDTO(dto: MembershipDTO, membership: Membership): Membership {
        dto.userId?.let { membership.userId = it }
        dto.version?.let { membership.version = it }

        if (hasAuthority(Role.BOARD)) {
            dto.startDate?.let { membership.startDate = it }
            membership.endDate = dto.endDate
            dto.memberType?.let { membership.memberType = it }
            membership.incasso = dto.incasso
        }

        return membership
    }

    override fun toDTO(membership: Membership): MembershipDTO {
        return MembershipDTO(
            userId = membership.userId,
            memberType = membership.memberType,
            city = null,
            country = null,
            startDate = membership.startDate,
            endDate = membership.endDate,
            incasso = membership.incasso
        ).also { dto ->
            dto.id = membership.id
            dto.version = membership.version
        }
    }
}

fun Membership.asDTO(mapper: MembershipMapper): MembershipDTO = mapper.toDTO(this)

fun MembershipDTO.asEntity(mapper: MembershipMapper): Membership = mapper.fromDTO(this)

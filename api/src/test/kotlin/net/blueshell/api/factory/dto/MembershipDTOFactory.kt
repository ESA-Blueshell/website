package net.blueshell.api.factory.dto

import net.blueshell.api.domain.membership.web.dto.MembershipDTO
import net.blueshell.api.shared.enums.MemberType
import org.springframework.stereotype.Component

/**
 * Factory for MembershipDTO test instances.
 */
@Component
class MembershipDTOFactory : BaseDtoFactory<net.blueshell.api.domain.membership.web.dto.MembershipDTO>() {

    override fun targetType(): Class<net.blueshell.api.domain.membership.web.dto.MembershipDTO> = _root_ide_package_.net.blueshell.api.domain.membership.web.dto.MembershipDTO::class.java

    override fun createBasic(): net.blueshell.api.domain.membership.web.dto.MembershipDTO {
        val dto = _root_ide_package_.net.blueshell.api.domain.membership.web.dto.MembershipDTO()
        dto.userId = nextId()
        dto.memberType = MemberType.REGULAR
        dto.city = "Enschede"
        dto.country = "NL"
        dto.startDate = today()
        dto.endDate = null
        dto.incasso = true
        return dto
    }
}

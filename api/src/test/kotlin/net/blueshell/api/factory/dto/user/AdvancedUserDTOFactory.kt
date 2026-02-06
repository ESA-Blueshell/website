package net.blueshell.api.factory.dto.user

import net.blueshell.api.common.enums.Role
import net.blueshell.api.dto.user.AdvancedUserDTO
import net.blueshell.api.factory.dto.BaseDtoFactory
import org.springframework.stereotype.Component
import java.sql.Date
import java.time.LocalDate

/**
 * Factory for AdvancedUserDTO test instances.
 */
@Component
class AdvancedUserDTOFactory : BaseDtoFactory<AdvancedUserDTO>() {

    override fun targetType(): Class<AdvancedUserDTO> = AdvancedUserDTO::class.java

    override fun createBasic(): AdvancedUserDTO {
        val dto = AdvancedUserDTO()
        dto.initials = "TU"
        dto.firstName = "Test"
        dto.lastName = "User"
        dto.username = unique("user")
        dto.newsletter = false
        dto.discord = "testuser"
        dto.email = email("user")
        dto.phoneNumber = "+31612345678"
        dto.password = "Password123!"
        dto.roles = mutableSetOf<Role?>(Role.MEMBER)
        dto.dateOfBirth = Date.valueOf(LocalDate.of(1990, 1, 1))
        dto.nationality = "Dutch"
        dto.photoConsent = true
        dto.ehbo = false
        dto.bhv = false
        dto.enabled = true
        dto.gender = "Male"
        return dto
    }
}

package net.blueshell.api.factory.dto.user

import net.blueshell.api.user.dto.SimpleUserDTO
import net.blueshell.api.factory.dto.BaseDtoFactory
import org.springframework.stereotype.Component

/**
 * Factory for SimpleUserDTO test instances.
 */
@Component
class SimpleUserDTOFactory : BaseDtoFactory<SimpleUserDTO>() {

    override fun targetType(): Class<SimpleUserDTO> = SimpleUserDTO::class.java

    override fun createBasic(): SimpleUserDTO {
        val dto = SimpleUserDTO()
        dto.initials = "TU"
        dto.firstName = "Test"
        dto.lastName = "User"
        dto.prefix = null
        dto.username = unique("user")
        dto.newsletter = false
        dto.discord = "testuser"
        dto.email = email("user")
        dto.phoneNumber = "+31612345678"
        dto.password = "Password123!"
        return dto
    }
}

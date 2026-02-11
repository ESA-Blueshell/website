package net.blueshell.api.factory.dto

import net.blueshell.api.domain.auth.web.dto.response.AuthenticationDTO
import net.blueshell.api.shared.enums.Role
import org.springframework.stereotype.Component
import java.util.*

/**
 * Factory for AuthenticationDTO test instances.
 */
@Component
class AuthenticationDTOFactory : BaseDtoFactory<AuthenticationDTO>() {

    override fun targetType(): Class<AuthenticationDTO> = AuthenticationDTO::class.java

    override fun createBasic(): AuthenticationDTO {
        val token = Base64.getEncoder().encodeToString("t-${nextId()}".toByteArray())
        val userId = nextId()
        val username = unique("user")
        val exp = System.currentTimeMillis() + 3_600_000
        return AuthenticationDTO(token, userId, username, exp, mutableSetOf(Role.MEMBER), null)
    }
}

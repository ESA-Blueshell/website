package net.blueshell.api.factory.dto

import net.blueshell.api.shared.enums.Role
import net.blueshell.api.auth.api.dto.response.AuthenticationDTO
import org.springframework.stereotype.Component
import java.util.Base64

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

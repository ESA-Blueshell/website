package net.blueshell.api.factory.dto

import net.blueshell.api.dto.request.JwtRequest
import org.springframework.stereotype.Component

/**
 * Factory for JwtRequest test instances.
 */
@Component
class JwtRequestFactory : BaseDtoFactory<JwtRequest>() {

    override fun targetType(): Class<JwtRequest> = JwtRequest::class.java

    override fun createBasic(): JwtRequest = JwtRequest(unique("user"), "Password123!")
}

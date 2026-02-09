package net.blueshell.api.factory.dto.request

import net.blueshell.api.auth.web.dto.recovery.PasswordResetRequest
import net.blueshell.api.factory.dto.BaseDtoFactory
import org.springframework.stereotype.Component

/**
 * Factory for PasswordResetRequest test instances.
 */
@Component
class PasswordResetRequestFactory : BaseDtoFactory<PasswordResetRequest>() {

    override fun targetType(): Class<PasswordResetRequest> = PasswordResetRequest::class.java

    override fun createBasic(): PasswordResetRequest {
        val dto = PasswordResetRequest()
        dto.token = unique("tok")
        dto.password = "Password123!"
        return dto
    }
}

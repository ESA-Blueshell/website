package net.blueshell.api.factory.dto.request

import net.blueshell.api.domain.auth.web.dto.recovery.UserActivationRequest
import net.blueshell.api.factory.dto.BaseDtoFactory
import org.springframework.stereotype.Component

/**
 * Factory for UserActivationRequest test instances.
 */
@Component
class UserActivationRequestFactory : BaseDtoFactory<UserActivationRequest>() {

    override fun targetType(): Class<UserActivationRequest> = UserActivationRequest::class.java

    override fun createBasic(): UserActivationRequest {
        val dto = UserActivationRequest()
        dto.token = unique("tok")
        return dto
    }
}

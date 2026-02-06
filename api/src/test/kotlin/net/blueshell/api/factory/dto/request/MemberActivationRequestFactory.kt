package net.blueshell.api.factory.dto.request

import net.blueshell.api.dto.recovery.MemberActivationRequest
import net.blueshell.api.factory.dto.BaseDtoFactory
import org.springframework.stereotype.Component

/**
 * Factory for MemberActivationRequest test instances.
 */
@Component
class MemberActivationRequestFactory : BaseDtoFactory<MemberActivationRequest>() {

    override fun targetType(): Class<MemberActivationRequest> = MemberActivationRequest::class.java

    override fun createBasic(): MemberActivationRequest {
        val dto = MemberActivationRequest()
        dto.token = unique("tok")
        dto.username = unique("user")
        dto.password = "Password123!"
        return dto
    }
}

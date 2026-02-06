package net.blueshell.api.factory.dto.request

import net.blueshell.api.factory.dto.DtoFactoryTestSupport
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

class MemberActivationRequestFactoryTest : DtoFactoryTestSupport() {

    @Autowired
    private lateinit var memberActivationRequestFactory: MemberActivationRequestFactory

    @Test
    fun `createBasic and createFull produce valid dto`() {
        assertFactoryProducesValidDtos(memberActivationRequestFactory)
    }
}

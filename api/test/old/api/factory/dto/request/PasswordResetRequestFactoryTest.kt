package net.blueshell.api.factory.dto.request

import net.blueshell.api.factory.dto.DtoFactoryTestSupport
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

class PasswordResetRequestFactoryTest : DtoFactoryTestSupport() {

    @Autowired
    private lateinit var passwordResetRequestFactory: PasswordResetRequestFactory

    @Test
    fun `createBasic and createFull produce valid dto`() {
        assertFactoryProducesValidDtos(passwordResetRequestFactory)
    }
}

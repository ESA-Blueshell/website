package net.blueshell.api.factory.dto.response

import net.blueshell.api.factory.dto.DtoFactoryTestSupport
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

class RedirectResponseDTOFactoryTest : DtoFactoryTestSupport() {

    @Autowired
    private lateinit var redirectResponseDTOFactory: RedirectResponseDTOFactory

    @Test
    fun `createBasic and createFull produce valid dto`() {
        assertFactoryProducesValidDtos(redirectResponseDTOFactory)
    }
}

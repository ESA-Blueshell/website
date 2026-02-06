package net.blueshell.api.factory.dto

import net.blueshell.api.factory.dto.DtoFactoryTestSupport
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

class RedirectDTOFactoryTest : DtoFactoryTestSupport() {

    @Autowired
    private lateinit var redirectDTOFactory: RedirectDTOFactory

    @Test
    fun `createBasic and createFull produce valid dto`() {
        assertFactoryProducesValidDtos(redirectDTOFactory)
    }
}

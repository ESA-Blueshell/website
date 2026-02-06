package net.blueshell.api.factory.dto.event

import net.blueshell.api.factory.dto.DtoFactoryTestSupport
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

class EventSignUpDTOFactoryTest : DtoFactoryTestSupport() {

    @Autowired
    private lateinit var eventSignUpDTOFactory: EventSignUpDTOFactory

    @Test
    fun `createBasic and createFull produce valid dto`() {
        assertFactoryProducesValidDtos(eventSignUpDTOFactory)
    }
}

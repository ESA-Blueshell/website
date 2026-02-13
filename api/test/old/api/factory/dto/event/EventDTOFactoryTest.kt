package net.blueshell.api.factory.dto.event

import net.blueshell.api.factory.dto.DtoFactoryTestSupport
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

class EventDTOFactoryTest : DtoFactoryTestSupport() {

    @Autowired
    private lateinit var eventDTOFactory: EventDTOFactory

    @Test
    fun `createBasic and createFull produce valid dto`() {
        assertFactoryProducesValidDtos(eventDTOFactory)
    }
}

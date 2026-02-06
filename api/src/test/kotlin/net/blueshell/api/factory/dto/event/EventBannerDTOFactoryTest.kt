package net.blueshell.api.factory.dto.event

import net.blueshell.api.factory.dto.DtoFactoryTestSupport
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

class EventBannerDTOFactoryTest : DtoFactoryTestSupport() {

    @Autowired
    private lateinit var eventBannerDTOFactory: EventBannerDTOFactory

    @Test
    fun `createBasic and createFull produce valid dto`() {
        assertFactoryProducesValidDtos(eventBannerDTOFactory)
    }
}

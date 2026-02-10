package net.blueshell.api.factory.dto

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

class SponsorDTOFactoryTest : DtoFactoryTestSupport() {

    @Autowired
    private lateinit var sponsorDTOFactory: SponsorDTOFactory

    @Test
    fun `createBasic and createFull produce valid dto`() {
        assertFactoryProducesValidDtos(sponsorDTOFactory)
    }
}

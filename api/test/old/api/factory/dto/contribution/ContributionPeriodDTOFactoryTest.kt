package net.blueshell.api.factory.dto.contribution

import net.blueshell.api.factory.dto.DtoFactoryTestSupport
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

class ContributionPeriodDTOFactoryTest : DtoFactoryTestSupport() {

    @Autowired
    private lateinit var contributionPeriodDTOFactory: ContributionPeriodDTOFactory

    @Test
    fun `createBasic and createFull produce valid dto`() {
        assertFactoryProducesValidDtos(contributionPeriodDTOFactory)
    }
}

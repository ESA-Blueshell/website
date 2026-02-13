package net.blueshell.api.factory.dto.contribution

import net.blueshell.api.factory.dto.DtoFactoryTestSupport
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

class ContributionReminderDTOFactoryTest : DtoFactoryTestSupport() {

    @Autowired
    private lateinit var contributionReminderDTOFactory: ContributionReminderDTOFactory

    @Test
    fun `createBasic and createFull produce valid dto`() {
        assertFactoryProducesValidDtos(contributionReminderDTOFactory)
    }
}

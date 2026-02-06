package net.blueshell.api.factory.dto.committee

import net.blueshell.api.factory.dto.DtoFactoryTestSupport
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

class AdvancedCommitteeDTOFactoryTest : DtoFactoryTestSupport() {

    @Autowired
    private lateinit var advancedCommitteeDTOFactory: AdvancedCommitteeDTOFactory

    @Test
    fun `createBasic and createFull produce valid dto`() {
        assertFactoryProducesValidDtos(advancedCommitteeDTOFactory)
    }

    @Test
    fun `assigns standard board roles`() {
        val dto = advancedCommitteeDTOFactory.createWithMemberCount(3)
        val roles = dto.members.mapNotNull { it.role }
        assertEquals(listOf("Chair", "Secretary", "Treasurer"), roles)
        assertNoViolations(dto)
    }

    @Test
    fun `rejects zero members`() {
        assertThrows(IllegalArgumentException::class.java) {
            advancedCommitteeDTOFactory.createWithMemberCount(0)
        }
    }
}

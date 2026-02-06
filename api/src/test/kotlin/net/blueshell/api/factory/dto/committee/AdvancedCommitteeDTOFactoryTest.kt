package net.blueshell.api.factory.dto.committee

import net.blueshell.api.factory.dto.DtoFactoryTestSupport
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

class AdvancedCommitteeDTOFactoryTest : DtoFactoryTestSupport() {

    @Autowired
    private lateinit var advancedCommitteeDTOFactory: AdvancedCommitteeDTOFactory

    @Autowired
    private lateinit var committeeMemberDTOFactory: CommitteeMemberDTOFactory

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
    fun `creates committee with explicit member roles`() {
        val dto = advancedCommitteeDTOFactory.createWithMemberRoles("Chair", "Secretary", "Member")
        val roles = dto.members.mapNotNull { it.role }
        assertEquals(listOf("Chair", "Secretary", "Member"), roles)
        assertNoViolations(dto)
    }

    @Test
    fun `creates single member committee`() {
        val dto = advancedCommitteeDTOFactory.createWithSingleMember()
        assertEquals(1, dto.members.size)
        assertNoViolations(dto)
    }

    @Test
    fun `creates standard board committee`() {
        val dto = advancedCommitteeDTOFactory.createWithStandardBoard()
        val roles = dto.members.mapNotNull { it.role }
        assertEquals(listOf("Chair", "Secretary", "Treasurer"), roles)
        assertNoViolations(dto)
    }

    @Test
    fun `creates large committee`() {
        val dto = advancedCommitteeDTOFactory.createWithLargeCommittee()
        assertEquals(7, dto.members.size)
        assertNoViolations(dto)
    }

    @Test
    fun `creates committee with custom members`() {
        val members = listOf(
            committeeMemberDTOFactory.createChair(),
            committeeMemberDTOFactory.createSecretary(),
            committeeMemberDTOFactory.createRegularMember()
        )
        val dto = advancedCommitteeDTOFactory.createWithCustomMembers(members)
        assertEquals(3, dto.members.size)
        assertNoViolations(dto)
    }

    @Test
    fun `rejects zero members`() {
        assertThrows(IllegalArgumentException::class.java) {
            advancedCommitteeDTOFactory.createWithMemberCount(0)
        }
    }
}

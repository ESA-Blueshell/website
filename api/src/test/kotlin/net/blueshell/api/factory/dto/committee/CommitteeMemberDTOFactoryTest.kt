package net.blueshell.api.factory.dto.committee

import net.blueshell.api.factory.dto.DtoFactoryTestSupport
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

class CommitteeMemberDTOFactoryTest : DtoFactoryTestSupport() {

    @Autowired
    private lateinit var committeeMemberDTOFactory: CommitteeMemberDTOFactory

    @Test
    fun `createBasic and createFull produce valid dto`() {
        assertFactoryProducesValidDtos(committeeMemberDTOFactory)
    }

    @Test
    fun `creates role helpers`() {
        val chair = committeeMemberDTOFactory.createChair()
        assertEquals("Chair", chair.role)
        assertNoViolations(chair)
    }

    @Test
    fun `creates secretary and treasurer roles`() {
        val secretary = committeeMemberDTOFactory.createSecretary()
        val treasurer = committeeMemberDTOFactory.createTreasurer()
        assertEquals("Secretary", secretary.role)
        assertEquals("Treasurer", treasurer.role)
        assertNoViolations(secretary)
        assertNoViolations(treasurer)
    }

    @Test
    fun `creates regular member role`() {
        val member = committeeMemberDTOFactory.createRegularMember()
        assertEquals("Member", member.role)
        assertNoViolations(member)
    }

    @Test
    fun `creates member with custom role`() {
        val dto = committeeMemberDTOFactory.createWithRole("Vice Chair")
        assertEquals("Vice Chair", dto.role)
        assertNoViolations(dto)
    }

    @Test
    fun `creates member with explicit ids`() {
        val dto = committeeMemberDTOFactory.createWithIds(10L, 20L)
        assertEquals(10L, dto.userId)
        assertEquals(20L, dto.committeeId)
        assertNoViolations(dto)
    }
}

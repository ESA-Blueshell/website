package net.blueshell.api.factory.dto.committee

import net.blueshell.api.domain.committee.web.dto.AdvancedCommitteeDTO
import net.blueshell.api.domain.committee.web.dto.CommitteeMemberDTO
import net.blueshell.api.factory.dto.BaseDtoFactory
import org.springframework.stereotype.Component

/**
 * Factory for AdvancedCommitteeDTO test instances.
 */
@Component
class AdvancedCommitteeDTOFactory(
    private val memberFactory: CommitteeMemberDTOFactory
) : BaseDtoFactory<AdvancedCommitteeDTO>() {

    override fun targetType(): Class<AdvancedCommitteeDTO> = AdvancedCommitteeDTO::class.java

    override fun createBasic(): AdvancedCommitteeDTO = createWithMemberCount(1)

    override fun createFull(): AdvancedCommitteeDTO = createWithMemberCount(3)

    /** Create with a specific number of members. */
    fun createWithMemberCount(memberCount: Int): AdvancedCommitteeDTO {
        require(memberCount >= 1) { "Member count must be at least 1" }

        val dto = AdvancedCommitteeDTO()
        dto.name = unique("Committee")
        dto.description = "Test committee description"

        val members = (0 until memberCount).map { index ->
            val member = memberFactory.createBasic()
            if (memberCount > 1) {
                member.role = getRoleForIndex(index, memberCount)
            }
            member
        }

        dto.members = members.toMutableList()
        return dto
    }

    /** Create with explicit member roles. */
    fun createWithMemberRoles(vararg roles: String): AdvancedCommitteeDTO {
        val dto = AdvancedCommitteeDTO()
        dto.name = unique("Committee")
        dto.description = "Test committee description"

        val members = roles.map { role ->
            val member = memberFactory.createBasic()
            member.role = role
            member
        }

        dto.members = members.toMutableList()
        return dto
    }

    private fun getRoleForIndex(index: Int, totalMembers: Int): String {
        if (totalMembers == 1) return "Chair"
        return when (index) {
            0 -> "Chair"
            1 -> "Secretary"
            2 -> "Treasurer"
            else -> "Member"
        }
    }

    // Convenience
    fun createWithSingleMember(): AdvancedCommitteeDTO = createWithMemberCount(1)

    fun createWithStandardBoard(): AdvancedCommitteeDTO = createWithMemberCount(3)

    fun createWithLargeCommittee(): AdvancedCommitteeDTO = createWithMemberCount(7)

    fun createWithCustomMembers(customMembers: List<CommitteeMemberDTO>): AdvancedCommitteeDTO {
        val dto = AdvancedCommitteeDTO()
        dto.name = unique("Committee")
        dto.description = "Test committee description"
        dto.members = customMembers.toMutableList()
        return dto
    }
}

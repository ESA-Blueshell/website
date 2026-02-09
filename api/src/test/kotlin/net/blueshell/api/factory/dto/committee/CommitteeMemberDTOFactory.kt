package net.blueshell.api.factory.dto.committee

import net.blueshell.api.committee.dto.CommitteeMemberDTO
import net.blueshell.api.factory.dto.BaseDtoFactory
import net.blueshell.api.factory.dto.user.SimpleUserDTOFactory
import org.springframework.stereotype.Component

/**
 * Factory for CommitteeMemberDTO test instances.
 */
@Component
class CommitteeMemberDTOFactory(
    @Suppress("UnusedPrivateMember")
    private val userFactory: SimpleUserDTOFactory
) : BaseDtoFactory<CommitteeMemberDTO>() {

    override fun targetType(): Class<CommitteeMemberDTO> = CommitteeMemberDTO::class.java

    override fun createBasic(): CommitteeMemberDTO {
        val dto = CommitteeMemberDTO()
        dto.userId = nextId()
        dto.committeeId = nextId()
        dto.role = "Member"
        return dto
    }

    fun createWithRole(role: String): CommitteeMemberDTO {
        val dto = createBasic()
        dto.role = role
        return dto
    }

    fun createWithIds(userId: Long, committeeId: Long): CommitteeMemberDTO {
        val dto = createBasic()
        dto.userId = userId
        dto.committeeId = committeeId
        return dto
    }

    fun createChair(): CommitteeMemberDTO = createWithRole("Chair")
    fun createSecretary(): CommitteeMemberDTO = createWithRole("Secretary")
    fun createTreasurer(): CommitteeMemberDTO = createWithRole("Treasurer")
    fun createRegularMember(): CommitteeMemberDTO = createWithRole("Member")
}

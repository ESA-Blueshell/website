package net.blueshell.api.committee.web.mapper

import net.blueshell.api.factory.dto.committee.CommitteeMemberDTOFactory
import net.blueshell.api.factory.model.committee.CommitteeMemberFactory
import net.blueshell.api.shared.mapper.MapperTestSupport
import net.blueshell.api.committee.web.mapper.CommitteeMemberMapper
import net.blueshell.api.committee.persistence.CommitteeMember
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest
class CommitteeMemberMapperIT @Autowired constructor(
    private val committeeMemberMapper: CommitteeMemberMapper,
    private val committeeMemberDTOFactory: CommitteeMemberDTOFactory,
    private val committeeMemberFactory: CommitteeMemberFactory
) : MapperTestSupport() {
    @Nested
    inner class ToDTO {
        @Test
        fun `maps persisted member`() {
            val committee = persistCommittee()
            val user = persistUser()
            val member = persist(committeeMemberFactory.createBasic(user, committee))

            val dto = committeeMemberMapper.toDTO(member)

            assertThat(dto.committeeId).isEqualTo(member.committeeId)
            assertThat(dto.userId).isEqualTo(member.userId)
            assertThat(dto.role).isEqualTo(member.role)
        }
    }

    @Nested
    inner class FromDTO {
        @Test
        fun `persists ids and role`() {
            val committee = persistCommittee()
            val user = persistUser()
            val member = committeeMemberFactory.createBasic(user, committee)
            val dto = committeeMemberDTOFactory.createBasic().apply {
                committeeId = committee.id
                userId = user.id
                role = "Chair"
            }

            val mapped = committeeMemberMapper.fromDTO(dto, member)
            val saved = persist(mapped)
            flushAndClear()

            val reloaded = reload(CommitteeMember::class.java, saved.id)

            assertThat(reloaded.committeeId).isEqualTo(committee.id)
            assertThat(reloaded.userId).isEqualTo(user.id)
            assertThat(reloaded.role).isEqualTo(dto.role)
        }
    }
}

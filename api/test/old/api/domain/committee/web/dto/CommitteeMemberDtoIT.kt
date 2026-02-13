package net.blueshell.api.domain.committee.web.dto

import net.blueshell.api.domain.committee.application.CommitteeMemberService
import net.blueshell.api.domain.committee.persistence.CommitteeMember
import net.blueshell.api.domain.committee.web.mapping.asEntity
import net.blueshell.api.factory.dto.committee.CommitteeMemberDTOFactory
import net.blueshell.api.factory.model.committee.CommitteeMemberFactory
import net.blueshell.api.shared.mapper.MapperTestSupport
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest
class CommitteeMemberDtoIT @Autowired constructor(
    private val committeeMemberDTOFactory: CommitteeMemberDTOFactory,
    private val committeeMemberFactory: CommitteeMemberFactory,
    private val committeeMemberService: CommitteeMemberService
) : MapperTestSupport() {
    @Nested
    inner class AsEntity {
        @Test
        fun `persists ids and role`() {
            val committee = persistCommittee()
            val user = persistUser()
            val member = committeeMemberFactory.createBasic(user, committee)
            val dto = committeeMemberDTOFactory.createBasic().apply {
                committeeId = committee.id!!
                userId = user.id!!
                role = "Chair"
            }

            val mapped = dto.asEntity(member)
            val saved = committeeMemberService.create(mapped)
            flushAndClear()

            val reloaded = reload(CommitteeMember::class.java, saved.id)

            assertThat(reloaded.committeeId).isEqualTo(committee.id)
            assertThat(reloaded.userId).isEqualTo(user.id)
            assertThat(reloaded.role).isEqualTo(dto.role)
        }
    }
}

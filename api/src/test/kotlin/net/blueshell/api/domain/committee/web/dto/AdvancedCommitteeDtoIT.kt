package net.blueshell.api.domain.committee.web.dto

import net.blueshell.api.domain.committee.application.CommitteeService
import net.blueshell.api.domain.committee.persistence.Committee
import net.blueshell.api.domain.committee.web.mapping.asEntity
import net.blueshell.api.factory.dto.committee.AdvancedCommitteeDTOFactory
import net.blueshell.api.shared.mapper.MapperTestSupport
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest
class AdvancedCommitteeDtoIT @Autowired constructor(
    private val advancedCommitteeDTOFactory: AdvancedCommitteeDTOFactory,
    private val committeeService: CommitteeService
) : MapperTestSupport() {
    @Nested
    inner class AsEntity {
        @Test
        fun `persists mapped committee with members`() {
            val chair = persistUser()
            val member = persistUser()
            val dto = advancedCommitteeDTOFactory.createWithMemberRoles("Chair", "Member").also {
                it.name = "Mapper Committee"
                it.description = "Committee mapped from DTO factory"
                it.members!![0].userId = chair.id!!
                it.members!![1].userId = member.id!!
            }
            val committee = committeeFactory.createBasic()

            val mapped = dto.asEntity(committee)
            val saved = committeeService.create(mapped)
            flushAndClear()

            val reloaded = reload(Committee::class.java, saved.id!!)

            assertThat(reloaded.name).isEqualTo(dto.name)
            assertThat(reloaded.description).isEqualTo(dto.description)
            assertThat(reloaded.members).hasSize(2)
            assertThat(reloaded.members.map { it.userId }.toSet())
                .containsExactlyInAnyOrder(chair.id, member.id)
            assertThat(reloaded.members.map { it.role }.toSet())
                .containsExactlyInAnyOrder("Chair", "Member")
        }
    }
}

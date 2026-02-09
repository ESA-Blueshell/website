package net.blueshell.api.committee.api.mapper

import net.blueshell.api.factory.dto.committee.AdvancedCommitteeDTOFactory
import net.blueshell.api.factory.model.committee.CommitteeMemberFactory
import net.blueshell.api.shared.mapper.MapperTestSupport
import net.blueshell.api.committee.api.mapper.AdvancedCommitteeMapper
import net.blueshell.api.committee.domain.model.Committee
import net.blueshell.api.committee.application.CommitteeService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest
class AdvancedCommitteeMapperIT @Autowired constructor(
    private val advancedCommitteeMapper: AdvancedCommitteeMapper,
    private val advancedCommitteeDTOFactory: AdvancedCommitteeDTOFactory,
    private val committeeMemberFactory: CommitteeMemberFactory,
    private val committeeService: CommitteeService
) : MapperTestSupport() {
    @Nested
    inner class ToDTO {
        @Test
        fun `maps committee with members`() {
            val committee = persist(committeeFactory.createBasic())
            val user = persistUser()
            val member = committeeMemberFactory.createWithCustomizations({ it.role = "Chair" }, user, committee)
            persist(member)
            flushAndClear()

            val reloaded = reload(Committee::class.java, committee.id!!)
            val dto = advancedCommitteeMapper.toDTO(reloaded)

            assertThat(dto.id).isEqualTo(reloaded.id)
            assertThat(dto.name).isEqualTo(reloaded.name)
            assertThat(dto.description).isEqualTo(reloaded.description)
            assertThat(dto.members).hasSize(1)
            assertThat(dto.members.first().userId).isEqualTo(user.id)
            assertThat(dto.members.first().role).isEqualTo("Chair")
        }
    }

    @Nested
    inner class FromDTO {
        @Test
        fun `persists mapped committee with members`() {
            val chair = persistUser()
            val member = persistUser()
            val dto = advancedCommitteeDTOFactory.createWithMemberRoles("Chair", "Member").also {
                it.name = "Mapper Committee"
                it.description = "Committee mapped from DTO factory"
                it.members[0].userId = chair.id
                it.members[1].userId = member.id
                it.members.forEach { memberDto -> memberDto.committeeId = null }
            }
            val committee = committeeFactory.createBasic()

            val mapped = advancedCommitteeMapper.fromDTO(dto, committee)
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

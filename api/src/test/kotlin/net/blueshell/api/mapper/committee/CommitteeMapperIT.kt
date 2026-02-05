package net.blueshell.api.mapper.committee

import jakarta.persistence.EntityManager
import net.blueshell.api.common.enums.Role
import net.blueshell.api.config.TruncateTestDatabaseListener
import net.blueshell.api.factory.dto.committee.AdvancedCommitteeDTOFactory
import net.blueshell.api.factory.model.UserFactory
import net.blueshell.api.repository.UserRepository
import net.blueshell.api.repository.committee.CommitteeRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestExecutionListeners

@SpringBootTest
@ActiveProfiles("test")
@TestExecutionListeners(
    listeners = [TruncateTestDatabaseListener::class],
    mergeMode = TestExecutionListeners.MergeMode.MERGE_WITH_DEFAULTS
)
class CommitteeMapperIT @Autowired constructor(
    private val committeeDTOFactory: AdvancedCommitteeDTOFactory,
    private val committeeMapper: AdvancedCommitteeMapper,
    private val committeeRepository: CommitteeRepository,
    private val userFactory: UserFactory,
    private val userRepository: UserRepository,
    private val entityManager: EntityManager
) {
    @Test
    fun `maps advanced committee dto and persists committee`() {
        val chair = userRepository.save(userFactory.createWithRole(Role.BOARD))
        val member = userRepository.save(userFactory.createWithRole(Role.MEMBER))

        val dto = committeeDTOFactory.createWithMemberRoles("Chair", "Member").also {
            it.name = "Mapper Committee"
            it.description = "Committee mapped from DTO factory"
            it.members[0]!!.userId = chair.id
            it.members[1]!!.userId = member.id
        }

        val committee = committeeMapper.fromDTO(dto)
        assertThat(committee.name)
            .`as`("Expected committee name to be mapped from DTO before saving")
            .isEqualTo(dto.name)

        assertThat(committee.description)
            .`as`("Expected committee description to be mapped from DTO before saving")
            .isEqualTo(dto.description)

        assertThat(committee.members)
            .`as`("Expected committee members to be mapped from DTO before saving")
            .hasSize(2)

        val mappedUserIds = committee.members.map { it.userId }.toSet()
        assertThat(mappedUserIds)
            .`as`("Expected committee members to reference the saved users before saving")
            .containsExactlyInAnyOrder(chair.id, member.id)

        val mappedRoles = committee.members.map { it.role }.toSet()
        assertThat(mappedRoles)
            .`as`("Expected committee member roles to be mapped from DTO before saving")
            .containsExactlyInAnyOrder("Chair", "Member")

        val saved = committeeRepository.saveAndFlush(committee)
        entityManager.clear()

        assertThat(saved.id)
            .`as`("Expected committee to be persisted and assigned an id")
            .isNotNull

        val reloaded = committeeRepository.findById(saved.id!!).orElseThrow {
            AssertionError("Expected committee to be persisted but could not find id ${saved.id}")
        }

        assertThat(reloaded.name)
            .`as`("Expected committee name to be mapped from DTO")
            .isEqualTo(dto.name)

        assertThat(reloaded.description)
            .`as`("Expected committee description to be mapped from DTO")
            .isEqualTo(dto.description)

        assertThat(reloaded.members)
            .`as`("Expected committee members to be persisted with the committee")
            .hasSize(2)

        val reloadedUserIds = reloaded.members.map { it.userId }.toSet()
        assertThat(reloadedUserIds)
            .`as`("Expected committee members to reference the saved users")
            .containsExactlyInAnyOrder(chair.id, member.id)

        val reloadedRoles = reloaded.members.map { it.role }.toSet()
        assertThat(reloadedRoles)
            .`as`("Expected committee member roles to be mapped from DTO")
            .containsExactlyInAnyOrder("Chair", "Member")
    }
}

package net.blueshell.api.domain.committee.persistence

import net.blueshell.api.domain.committee.web.mapping.asAdvancedDto
import net.blueshell.api.domain.committee.web.mapping.asSimpleDto
import net.blueshell.api.shared.model.ModelPersistenceTestSupport
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class CommitteeModelIT : ModelPersistenceTestSupport() {

    @Nested
    inner class Persistence {

        @Test
        fun `persists column fields`() {
            val committee = committeeFactory.createBasic()
            committee.name = unique("committee")
            committee.description = "Description"

            val found = persistAndReload(committee, Committee::class.java) { it.id }

            assertEquals(committee.name, found.name)
            assertEquals(committee.description, found.description)
        }

        @Test
        fun `persists members relation when setting entity`() {
            val committee = committeeFactory.createBasic()
            committee.name = unique("committee")
            committee.description = "Description"

            val userOne = persist(userFactory.createBasic())
            val userTwo = persist(userFactory.createBasic())
            val memberOne = committeeMemberFactory.createBasic(userOne, committee)
            val memberTwo = committeeMemberFactory.createBasic(userTwo, committee)
            committee.replaceMembers(listOf(memberOne, memberTwo))

            val found = persistAndReload(committee, Committee::class.java) { it.id }

            assertEquals(2, found.members.size)
        }

        @Test
        fun `persists members relation when setting id`() {
            val committee = committeeFactory.createBasic()
            committee.name = unique("committee")
            committee.description = "Description"
            val savedCommittee = persist(committee)

            val userOne = persist(userFactory.createBasic())
            val userTwo = persist(userFactory.createBasic())
            val memberOne = _root_ide_package_.net.blueshell.api.domain.committee.persistence.CommitteeMember()
            memberOne.user = userOne
            memberOne.committee = savedCommittee
            val memberTwo = _root_ide_package_.net.blueshell.api.domain.committee.persistence.CommitteeMember()
            memberTwo.user = userTwo
            memberTwo.committee = savedCommittee
            persist(memberOne)
            persist(memberTwo)

            entityManager.flush()
            entityManager.clear()

            val found = entityManager.find(Committee::class.java, savedCommittee.id)
            assertEquals(2, found.members.size)
        }
    }

    @Nested
    inner class AsDto {
        @Test
        fun `maps committee with members to advanced dto`() {
            val committee = persist(committeeFactory.createBasic())
            val user = persist(userFactory.createBasic())
            val member = committeeMemberFactory.createWithCustomizations({ it.role = "Chair" }, user, committee)
            persist(member)
            entityManager.flush()
            entityManager.clear()

            val reloaded = entityManager.find(Committee::class.java, committee.id)
            val dto = reloaded.asAdvancedDto()

            assertEquals(reloaded.id, dto.id)
            assertEquals(reloaded.name, dto.name)
            assertEquals(reloaded.description, dto.description)
            assertEquals(1, dto.members!!.size)
            assertEquals(user.id, dto.members!!.first().userId)
            assertEquals("Chair", dto.members!!.first().role)
        }

        @Test
        fun `maps core fields to simple dto`() {
            val committee = persistCommittee()

            val dto = committee.asSimpleDto()

            assertEquals(committee.name, dto.name)
            assertEquals(committee.description, dto.description)
        }
    }
}

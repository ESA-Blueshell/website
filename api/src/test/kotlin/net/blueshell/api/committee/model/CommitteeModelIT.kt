package net.blueshell.api.committee.model

import net.blueshell.api.shared.model.ModelPersistenceTestSupport
import net.blueshell.api.committee.model.Committee
import net.blueshell.api.committee.model.CommitteeMember
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
            committee.members = listOf(memberOne, memberTwo)

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
            val memberOne = CommitteeMember()
            memberOne.user = userOne
            memberOne.committeeId = savedCommittee.id!!
            val memberTwo = CommitteeMember()
            memberTwo.user = userTwo
            memberTwo.committeeId = savedCommittee.id!!
            persist(memberOne)
            persist(memberTwo)

            entityManager.flush()
            entityManager.clear()

            val found = entityManager.find(Committee::class.java, savedCommittee.id)
            assertEquals(2, found.members.size)
        }
    }
}

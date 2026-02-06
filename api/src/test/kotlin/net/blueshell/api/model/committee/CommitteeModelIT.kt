package net.blueshell.api.model.committee

import net.blueshell.api.model.ModelPersistenceTestSupport
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class CommitteeModelIT : ModelPersistenceTestSupport() {

    @Nested
    inner class Persistence {

        @Test
        fun persists_columns_and_members_relation() {
            val committee = committeeFactory.createBasic()
            committee.name = unique("committee")
            committee.description = "Description"

            val user = persist(userFactory.createBasic())
            val member = committeeMemberFactory.createBasic(user, committee)
            committee.members = listOf(member)

            val found = persistAndReload(committee, Committee::class.java) { it.id }

            assertEquals(committee.name, found.name)
            assertEquals(committee.description, found.description)
            assertEquals(1, found.members.size)
            assertEquals(member.role, found.members.first().role)
            assertEquals(user.id, found.members.first().user.id)
        }
    }
}

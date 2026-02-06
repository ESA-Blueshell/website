package net.blueshell.api.integration.model.committee

import net.blueshell.api.integration.model.ModelPersistenceTestSupport
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class CommitteeMemberModelIT : net.blueshell.api.integration.model.ModelPersistenceTestSupport() {

    @Nested
    inner class Persistence {

        @Test
        fun persists_join_columns_and_role() {
            val committee = persistCommittee()
            val user = persist(userFactory.createBasic())

            val member = committeeMemberFactory.createBasic(user, committee)
            member.role = "Chair"

            val found = persistAndReload(member, CommitteeMember::class.java) { it.id }

            assertEquals(committee.id, found.committeeId)
            assertEquals(user.id, found.userId)
            assertEquals("Chair", found.role)
        }
    }
}

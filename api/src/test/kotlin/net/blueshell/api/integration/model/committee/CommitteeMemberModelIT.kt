package net.blueshell.api.integration.model.committee

import net.blueshell.api.integration.model.ModelPersistenceTestSupport
import net.blueshell.api.model.committee.CommitteeMember
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class CommitteeMemberModelIT : ModelPersistenceTestSupport() {

    @Nested
    inner class Persistence {

        @Test
        fun `persists column fields`() {
            val committee = persistCommittee()
            val user = persist(userFactory.createBasic())

            val member = committeeMemberFactory.createBasic(user, committee)
            member.role = "Chair"

            val found = persistAndReload(member, CommitteeMember::class.java) { it.id }

            assertEquals("Chair", found.role)
        }

        @Test
        fun `persists committee relation when setting entity`() {
            val committee = persistCommittee()
            val user = persist(userFactory.createBasic())

            val member = committeeMemberFactory.createBasic(user, committee)

            val found = persistAndReload(member, CommitteeMember::class.java) { it.id }

            assertEquals(committee.id, found.committeeId)
            assertEquals(committee.id, found.committee.id)
        }

        @Test
        fun `persists committee relation when setting id`() {
            val committee = persistCommittee()
            val user = persist(userFactory.createBasic())

            val member = CommitteeMember()
            member.user = user
            member.committeeId = committee.id!!

            val found = persistAndReload(member, CommitteeMember::class.java) { it.id }

            assertEquals(committee.id, found.committeeId)
            assertEquals(committee.id, found.committee.id)
        }

        @Test
        fun `persists user relation when setting entity`() {
            val committee = persistCommittee()
            val user = persist(userFactory.createBasic())

            val member = committeeMemberFactory.createBasic(user, committee)

            val found = persistAndReload(member, CommitteeMember::class.java) { it.id }

            assertEquals(user.id, found.userId)
            assertEquals(user.id, found.user.id)
        }

        @Test
        fun `persists user relation when setting id`() {
            val committee = persistCommittee()
            val user = persist(userFactory.createBasic())

            val member = CommitteeMember()
            member.committee = committee
            member.userId = user.id!!

            val found = persistAndReload(member, CommitteeMember::class.java) { it.id }

            assertEquals(user.id, found.userId)
            assertEquals(user.id, found.user.id)
        }
    }
}

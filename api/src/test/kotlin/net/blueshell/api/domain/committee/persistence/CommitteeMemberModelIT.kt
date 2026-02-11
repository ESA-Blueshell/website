package net.blueshell.api.domain.committee.persistence

import net.blueshell.api.domain.committee.web.mapping.asDto
import net.blueshell.api.shared.model.ModelPersistenceTestSupport
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

            val member = _root_ide_package_.net.blueshell.api.domain.committee.persistence.CommitteeMember()
            member.user = user
            member.committee = committee

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

            val member = _root_ide_package_.net.blueshell.api.domain.committee.persistence.CommitteeMember()
            member.committee = committee
            member.user = user

            val found = persistAndReload(member, CommitteeMember::class.java) { it.id }

            assertEquals(user.id, found.userId)
            assertEquals(user.id, found.user.id)
        }
    }

    @Nested
    inner class AsDto {
        @Test
        fun `maps persisted member`() {
            val committee = persistCommittee()
            val user = persist(userFactory.createBasic())
            val member = persist(committeeMemberFactory.createBasic(user, committee))

            val dto = member.asDto()

            assertEquals(member.committeeId, dto.committeeId)
            assertEquals(member.userId, dto.userId)
            assertEquals(member.role, dto.role)
        }
    }
}

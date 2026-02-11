package net.blueshell.api.membership.persistence

import net.blueshell.api.domain.membership.web.mapping.asDto
import net.blueshell.api.shared.enums.MemberType
import net.blueshell.api.shared.model.ModelPersistenceTestSupport
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.LocalDate

class MembershipModelIT : ModelPersistenceTestSupport() {

    @Nested
    inner class Persistence {

        @Test
        fun `persists column fields`() {
            val user = persist(userFactory.createBasic())
            val membership = membershipFactory.createBasic(user)
            membership.startDate = LocalDate.of(2022, 5, 1)
            membership.endDate = LocalDate.of(2023, 5, 1)
            membership.memberType = MemberType.ALUMNI
            membership.incasso = true

            val found = persistAndReload(membership, _root_ide_package_.net.blueshell.api.domain.membership.persistence.Membership::class.java) { it.id }

            assertEquals(membership.startDate, found.startDate)
            assertEquals(membership.endDate, found.endDate)
            assertEquals(membership.memberType, found.memberType)
            assertEquals(membership.incasso, found.incasso)
        }

        @Test
        fun `persists user relation when setting entity`() {
            val user = persist(userFactory.createBasic())
            val membership = membershipFactory.createBasic(user)

            val found = persistAndReload(membership, _root_ide_package_.net.blueshell.api.domain.membership.persistence.Membership::class.java) { it.id }

            assertEquals(user.id, found.userId)
            assertEquals(user.id, found.user?.id)
        }

        @Test
        fun `persists user relation when setting id`() {
            val user = persist(userFactory.createBasic())
            val membership = membershipFactory.createBasic(user)
            membership.user = user

            val found = persistAndReload(membership, _root_ide_package_.net.blueshell.api.domain.membership.persistence.Membership::class.java) { it.id }

            assertEquals(user.id, found.userId)
            assertEquals(user.id, found.user.id)
        }
    }

    @Nested
    inner class AsDto {
        @Test
        fun `maps persisted membership`() {
            val user = persist(userFactory.createBasic())
            val membership = persist(membershipFactory.createBasic(user))

            val dto = membership.asDto()

            assertEquals(membership.id, dto.id)
            assertEquals(membership.userId, dto.userId)
            assertEquals(membership.memberType, dto.memberType)
            assertEquals(membership.startDate, dto.startDate)
            assertEquals(membership.endDate, dto.endDate)
            assertEquals(membership.incasso, dto.incasso)
        }
    }
}

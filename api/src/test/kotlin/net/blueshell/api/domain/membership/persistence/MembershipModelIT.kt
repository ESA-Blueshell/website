package net.blueshell.api.domain.membership.persistence

import net.blueshell.api.domain.membership.web.mapping.asDto
import net.blueshell.api.shared.enums.MemberType
import net.blueshell.api.shared.model.ModelPersistenceTestSupport
import org.junit.jupiter.api.Assertions
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

            val found = persistAndReload(membership, Membership::class.java) { it.id }

            Assertions.assertEquals(membership.startDate, found.startDate)
            Assertions.assertEquals(membership.endDate, found.endDate)
            Assertions.assertEquals(membership.memberType, found.memberType)
            Assertions.assertEquals(membership.incasso, found.incasso)
        }

        @Test
        fun `persists user relation when setting entity`() {
            val user = persist(userFactory.createBasic())
            val membership = membershipFactory.createBasic(user)

            val found = persistAndReload(membership, Membership::class.java) { it.id }

            Assertions.assertEquals(user.id, found.userId)
            Assertions.assertEquals(user.id, found.user.id)
        }
    }

    @Nested
    inner class AsDto {
        @Test
        fun `maps persisted membership`() {
            val user = persist(userFactory.createBasic())
            val membership = persist(membershipFactory.createBasic(user))

            val dto = membership.asDto()

            Assertions.assertEquals(membership.id, dto.id)
            Assertions.assertEquals(membership.userId, dto.userId)
            Assertions.assertEquals(membership.memberType, dto.memberType)
            Assertions.assertEquals(membership.startDate, dto.startDate)
            Assertions.assertEquals(membership.endDate, dto.endDate)
            Assertions.assertEquals(membership.incasso, dto.incasso)
        }
    }
}
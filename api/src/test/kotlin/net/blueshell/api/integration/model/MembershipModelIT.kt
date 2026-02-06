package net.blueshell.api.integration.model

import net.blueshell.api.common.enums.MemberType
import net.blueshell.api.model.Membership
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

            val found = persistAndReload(membership, Membership::class.java) { it.id }

            assertEquals(membership.startDate, found.startDate)
            assertEquals(membership.endDate, found.endDate)
            assertEquals(membership.memberType, found.memberType)
            assertEquals(membership.incasso, found.incasso)
        }

        @Test
        fun `persists user relation when setting entity`() {
            val user = persist(userFactory.createBasic())
            val membership = membershipFactory.createBasic(user)

            val found = persistAndReload(membership, Membership::class.java) { it.id }

            assertEquals(user.id, found.userId)
            assertEquals(user.id, found.user?.id)
        }

        @Test
        fun `persists user relation when setting id`() {
            val user = persist(userFactory.createBasic())
            val membership = membershipFactory.createBasic(user)
            membership.userId = user.id!!

            val found = persistAndReload(membership, Membership::class.java) { it.id }

            assertEquals(user.id, found.userId)
            assertEquals(user.id, found.user.id)
        }
    }
}

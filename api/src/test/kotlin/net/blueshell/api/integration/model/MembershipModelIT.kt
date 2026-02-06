package net.blueshell.api.integration.model

import net.blueshell.api.common.enums.MemberType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.LocalDate

class MembershipModelIT : net.blueshell.api.integration.model.ModelPersistenceTestSupport() {

    @Nested
    inner class Persistence {

        @Test
        fun persists_columns_and_user_relation() {
            val user = persist(userFactory.createBasic())
            val membership = membershipFactory.createBasic(user)
            membership.startDate = LocalDate.of(2022, 5, 1)
            membership.endDate = LocalDate.of(2023, 5, 1)
            membership.memberType = MemberType.ALUMNI
            membership.incasso = true

            val found = persistAndReload(membership, Membership::class.java) { it.id }

            assertEquals(user.id, found.userId)
            assertEquals(membership.startDate, found.startDate)
            assertEquals(membership.endDate, found.endDate)
            assertEquals(membership.memberType, found.memberType)
            assertEquals(membership.incasso, found.incasso)
        }
    }
}

package net.blueshell.api.membership.application

import net.blueshell.api.factory.model.MembershipFactory
import net.blueshell.api.factory.model.UserFactory
import net.blueshell.api.domain.membership.application.event.MembershipChanged
import net.blueshell.api.shared.enums.Role
import net.blueshell.api.testsupport.ServiceTestSupport
import net.blueshell.api.domain.user.application.UserService
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.LocalDate

class MembershipServiceIT : ServiceTestSupport() {

    @Autowired
    private lateinit var memberships: net.blueshell.api.domain.membership.application.MembershipService

    @Autowired
    private lateinit var users: UserService

    @Autowired
    private lateinit var userFactory: UserFactory

    @Autowired
    private lateinit var membershipFactory: MembershipFactory

    @Nested
    inner class Create {

        @Test
        fun `creates membership and adds member role`() {
            val user = persist(userFactory.createBasic())
            val membership = membershipFactory.createActive(user)

            memberships.create(membership)

            val updatedUser = users.findById(user.id!!)
            assertTrue(updatedUser.hasRole(Role.MEMBER))
            assertTrue(applicationEvents.stream(MembershipChanged::class.java).anyMatch { it.userId == user.id })
        }
    }

    @Nested
    inner class Update {

        @Test
        fun `ending membership removes member role`() {
            val user = persist(userFactory.createBasic())
            val membership = membershipFactory.createActive(user)
            val saved = memberships.create(membership)

            saved.endDate = LocalDate.now().minusDays(1)
            memberships.update(saved)

            val updatedUser = users.findById(user.id!!)
            assertFalse(updatedUser.hasRole(Role.MEMBER))
        }
    }
}

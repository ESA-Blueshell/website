package net.blueshell.api.membership.application

import net.blueshell.api.membership.application.event.MembershipChangedEvent
import net.blueshell.api.shared.enums.Role
import net.blueshell.api.testsupport.EventIntegrationTestSupport
import net.blueshell.api.factory.model.MembershipFactory
import net.blueshell.api.factory.model.UserFactory
import net.blueshell.api.user.application.UserService
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.LocalDate

class MembershipEventIT : EventIntegrationTestSupport() {

    @Autowired
    private lateinit var memberships: MembershipService

    @Autowired
    private lateinit var users: UserService

    @Autowired
    private lateinit var userFactory: UserFactory

    @Autowired
    private lateinit var membershipFactory: MembershipFactory

    @Test
    fun `creates membership and adds member role`() {
        val user = persist(userFactory.createBasic())
        val membership = membershipFactory.createActive(user)

        memberships.create(membership)

        val updatedUser = users.findById(user.id!!)
        assertTrue(updatedUser.hasRole(Role.MEMBER))
        assertTrue(applicationEvents.stream(MembershipChangedEvent::class.java).anyMatch { it.userId == user.id })
    }

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

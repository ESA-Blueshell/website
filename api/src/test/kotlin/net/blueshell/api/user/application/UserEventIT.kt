package net.blueshell.api.user.application

import net.blueshell.api.factory.model.UserFactory
import net.blueshell.api.platform.integration.contact.job.SyncContactJob
import net.blueshell.api.shared.enums.Role
import net.blueshell.api.testsupport.EventIntegrationTestSupport
import net.blueshell.api.user.application.event.UserCreated
import net.blueshell.api.user.application.event.UserUpdated
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

class UserEventIT : EventIntegrationTestSupport() {

    @Autowired
    private lateinit var users: UserService

    @Autowired
    private lateinit var userFactory: UserFactory

    @Test
    fun `publishes user created and sync contact events`() {
        val user = users.create(userFactory.createBasic())

        assertTrue(applicationEvents.stream(UserCreated::class.java).anyMatch { it.userId == user.id })
        assertTrue(jobExecutions.findByJobType(SyncContactJob.TYPE).isNotEmpty())
    }

    @Test
    fun `publishes user updated and sync contact events`() {
        val user = users.create(userFactory.createBasic())
        user.firstName += " Updated"
        user.roles = mutableSetOf(Role.GUEST)

        users.update(user)

        assertTrue(applicationEvents.stream(UserUpdated::class.java).anyMatch { it.userId == user.id })
        assertTrue(jobExecutions.findByJobType(SyncContactJob.TYPE).isNotEmpty())
    }
}

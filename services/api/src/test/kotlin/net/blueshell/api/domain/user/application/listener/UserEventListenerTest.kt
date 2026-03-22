package net.blueshell.api.domain.user.application.listener

import net.blueshell.api.domain.user.application.event.UserCreated
import net.blueshell.api.domain.user.application.event.UserUpdated
import net.blueshell.api.domain.user.persistence.User
import net.blueshell.api.shared.enums.Role
import net.blueshell.api.shared.job.ContactJobs
import net.blueshell.api.testsupport.ServiceTestSupport
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.crypto.password.PasswordEncoder

class UserEventListenerTest : ServiceTestSupport() {

    @Autowired
    private lateinit var listener: UserEventListener

    @Autowired
    private lateinit var passwordEncoder: PasswordEncoder

    @Test
    fun `dispatches per-integration contact sync on UserCreated`() {
        val user = createAndSaveUser("newuser", "newuser@example.com")
        val event = UserCreated(user.id!!)

        listener.onCreate(event)

        // MockContactIntegrationJobProvider dispatches listmonk.contact.sync
        val jobs = findJobsByType(ContactJobs.SyncContactToSystem.type)
        assertThat(jobs)
            .describedAs("Should schedule one SyncContactForSystem job")
            .hasSize(1)

        assertThat(jobs.first().payload)
            .contains("\"userId\":${user.id}")
    }

    @Test
    fun `dispatches per-integration contact sync on UserUpdated`() {
        val user = createAndSaveUser("existinguser", "existing@example.com")
        val event = UserUpdated(user.id!!)

        listener.onUpdate(event)

        val jobs = findJobsByType(ContactJobs.SyncContactToSystem.type)
        assertThat(jobs)
            .describedAs("Should schedule one SyncContactForSystem job")
            .hasSize(1)

        assertThat(jobs.first().payload)
            .contains("\"userId\":${user.id}")
    }

    private fun createAndSaveUser(username: String, email: String): User {
        val user = User(
            username = username,
            email = email,
            password = requireNotNull(passwordEncoder.encode("Password123!")) { "PasswordEncoder returned null hash" },
            initials = "TU",
            firstName = "Test",
            lastName = "User",
            phoneNumber = "06${System.currentTimeMillis().toString().takeLast(8)}",
            discord = "$username#0001"
        )
        user.enabled = true
        user.roles = mutableSetOf(Role.MEMBER)
        return persist(user)
    }
}
